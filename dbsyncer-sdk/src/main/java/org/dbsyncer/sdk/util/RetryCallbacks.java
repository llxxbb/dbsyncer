package org.dbsyncer.sdk.util;

import org.dbsyncer.common.retry.RetryAfter;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;


/**
 * RetryAfter 回调工厂
 * 提供 LogService 的回调实现
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
}
