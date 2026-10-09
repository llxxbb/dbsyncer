package org.dbsyncer.sdk.util;

import org.dbsyncer.common.retry.RetryAfter;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.MessageService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RetryAfter 回调工厂
 * 提供 LogService 和 MessageService 的回调实现
 */
public final class RetryCallbacks {

    private RetryCallbacks() {
    }

    /**
     * 创建持久化日志回调
     *
     * @param logService 日志服务
     * @param mapping    映射对象
     * @return 回调实现
     */
    public static RetryAfter logService(LogService logService, Mapping mapping) {
        return ctx -> {
            String msg = String.format("第%d次重试 | 间隔=%dms | 异常=%s",
                    ctx.getAttempt(), ctx.getIntervalMs(), ctx.getException().getMessage());
            logService.log(mapping, LogType.SYNC, LogLevel.WARN, msg);
        };
    }

    /**
     * 创建消息通知回调
     *
     * @param messageService 消息服务
     * @param mapping        映射对象
     * @param type           通知类型
     * @return 回调实现
     */
    public static RetryAfter messageService(MessageService messageService, Mapping mapping, NotifyType type) {
        if (messageService == null) {
            return ctx -> {
            };
        }
        return ctx -> {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("重试次数", "第" + ctx.getAttempt() + "次");
            details.put("重试间隔", ctx.getIntervalMs() + "ms");
            details.put("异常信息", ctx.getException().getMessage());
            messageService.sendNotifyMessage(mapping, "重试通知", type, details);
        };
    }
}
