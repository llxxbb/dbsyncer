package org.dbsyncer.sdk.util;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.retry.RetryAfter;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.MessageService;

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
        return messageService(messageService, mapping, type, null);
    }

    /**
     * 创建消息通知回调（带通知配置）
     *
     * @param messageService 消息服务
     * @param mapping        映射对象
     * @param type           通知类型
     * @param props          通知配置
     * @return 回调实现
     */
    public static RetryAfter messageService(MessageService messageService, Mapping mapping, NotifyType type,
            NotifyProps props) {
        if (messageService == null) {
            return ctx -> {
            };
        }
        return ctx -> {
            StringBuilder msg = new StringBuilder();
            msg.append("### DBSyncer 重试通知\n\n");
            msg.append("**操作类型**: ").append(type.getName()).append("\n\n");
            msg.append("**Mapping ID**: ").append(mapping.getId()).append("\n\n");
            msg.append("**任务名称**: ").append(mapping.getName()).append("\n\n");
            msg.append("**重试次数**: 第").append(ctx.getAttempt()).append("次\n\n");
            msg.append("**重试间隔**: ").append(ctx.getIntervalMs()).append("ms\n\n");
            msg.append("**异常信息**: ").append(ctx.getException().getMessage()).append("\n\n");
            messageService.sendMessage("dbsyncer", msg.toString(), type, props);
        };
    }
}
