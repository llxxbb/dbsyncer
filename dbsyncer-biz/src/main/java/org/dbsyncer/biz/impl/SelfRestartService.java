package org.dbsyncer.biz.impl;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * ADR-0029 §2.0.4：B 类自重启服务。
 * <p>
 * 保存 B 类配置后：
 * 1. 发送通知（操作人、变更配置、重启时间）
 * 2. 延迟 3 秒（给通知发送时间）
 * 3. 优雅停机（System.exit(0) 或 SpringApplication.exit）
 * <p>
 * 安全约束：
 * - 必须检测进程守护是否存在（systemd/docker），无守护时降级为仅提示
 * - 重启前必须确保 DB 提交 + sidecar 重写都已成功
 */
@Service
public class SelfRestartService {

    private static final Logger logger = LoggerFactory.getLogger(SelfRestartService.class);

    private static final int RESTART_DELAY_SECONDS = 3;

    @Resource
    private NotifyService notifyService;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 是否需要自重启。
     * 检测进程守护是否存在，无守护时降级为仅提示。
     *
     * @return true=可以自重启，false=需要人工重启
     */
    public boolean isSelfRestartPossible() {
        // 检测 systemd
        if (isSystemdManaged()) {
            return true;
        }
        // 检测 docker
        if (isDockerContainer()) {
            return true;
        }
        // 检测配置开关
        String enabled = System.getProperty("dbsyncer.runtime.self-restart.enabled");
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        return false;
    }

    /**
     * 执行自重启流程。
     *
     * @param changedConfigKeys 变更的配置项列表
     * @param currentUser       操作人
     */
    public void scheduleRestart(java.util.List<String> changedConfigKeys, String currentUser) {
        if (!isSelfRestartPossible()) {
            logger.warn("未检测到进程守护（systemd/docker），不执行自重启。请手动重启应用。");
            return;
        }

        // 1. 发送通知
        try {
            String message = String.format(
                    "系统参数已更新，应用即将重启。\n" +
                            "操作人：%s\n" +
                            "变更配置：%s\n" +
                            "重启时间：%s",
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
            logger.warn("发送重启通知失败: {}", e.getMessage());
        }

        // 2. 延迟 3 秒后重启
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(RESTART_DELAY_SECONDS * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("===== 自重启开始 =====");
            gracefulShutdown();
        });
    }

    /**
     * 优雅停机。
     */
    private void gracefulShutdown() {
        try {
            if (applicationContext instanceof ConfigurableApplicationContext) {
                ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) applicationContext;
                ctx.close();
            }
        } catch (Exception e) {
            logger.warn("优雅关闭 ApplicationContext 失败: {}", e.getMessage());
        } finally {
            // 确保进程退出
            System.exit(0);
        }
    }

    /**
     * 检测是否被 systemd 管理。
     */
    private boolean isSystemdManaged() {
        try {
            // 检测 /proc/1/comm 是否为 systemd
            File proc1Comm = new File("/proc/1/comm");
            if (proc1Comm.exists()) {
                String comm = new String(java.nio.file.Files.readAllBytes(proc1Comm.toPath())).trim();
                return "systemd".equals(comm);
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * 检测是否在 Docker 容器中。
     */
    private boolean isDockerContainer() {
        try {
            // 检测 /.dockerenv 文件
            if (new File("/.dockerenv").exists()) {
                return true;
            }
            // 检测 cgroup 中是否包含 docker
            File cgroup = new File("/proc/1/cgroup");
            if (cgroup.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(cgroup.toPath()));
                return content.contains("docker") || content.contains("containerd");
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}
