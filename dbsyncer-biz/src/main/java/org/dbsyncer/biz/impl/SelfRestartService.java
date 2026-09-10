package org.dbsyncer.biz.impl;

import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ADR-0031：B 类配置热重载服务（替代原进程级重启）。
 * <p>
 * 保存 B 类配置后：
 * 1. 发送通知（操作人、变更配置、重载时间）
 * 2. 延迟 3 秒（给通知发送时间）
 * 3. 委托 BusinessReloader 执行业务热重载
 */
@Service
public class SelfRestartService {

    private static final Logger logger = LoggerFactory.getLogger(SelfRestartService.class);

    private static final int RESTART_DELAY_SECONDS = 3;

    @Resource
    private NotifyService notifyService;

    @Resource
    private BusinessReloader businessReloader;

    /**
     * 执行热重载流程。
     *
     * @param changedConfigKeys 变更的配置项列表
     * @param currentUser       操作人
     */
    public void scheduleRestart(List<String> changedConfigKeys, String currentUser) {
        // 1. 发送通知
        try {
            String message = String.format(
                    "系统参数已更新，应用即将热重载。\n" +
                            "操作人：%s\n" +
                            "变更配置：%s\n" +
                            "重载时间：%s",
                    currentUser != null ? currentUser : "admin",
                    changedConfigKeys != null ? String.join(", ", changedConfigKeys) : "B 类配置",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            NotifyMessage notifyMessage = NotifyMessage.newBuilder()
                    .setTitle("系统配置变更通知")
                    .setContent(message)
                    .setType(NotifyType.MANUAL_OPERATION)
                    .setReceivers(Collections.emptyList());
            notifyService.sendMessage(notifyMessage);
        } catch (Exception e) {
            logger.warn("发送重载通知失败: {}", e.getMessage());
        }

        // 2. 延迟 3 秒后执行热重载（使用独立调度线程，避免依赖 ForkJoinPool.commonPool）
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hot-reload");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(() -> {
            try {
                logger.info("===== 业务热重载开始 =====");
                businessReloader.reload(changedConfigKeys);
                logger.info("===== 业务热重载完成 =====");
            } catch (Exception e) {
                logger.error("业务热重载执行失败", e);
            } finally {
                // 任务完成后释放线程，避免每次热重载泄漏一个 ScheduledExecutorService
                scheduler.shutdown();
            }
        }, RESTART_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
