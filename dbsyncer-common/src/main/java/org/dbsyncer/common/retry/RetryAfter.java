package org.dbsyncer.common.retry;

import org.slf4j.Logger;

/**
 * 重试后回调接口
 * 用于在每次重试触发时执行自定义逻辑（如记录日志、发送通知等）
 */
public interface RetryAfter {

    /**
     * 重试触发后的回调方法
     *
     * @param ctx 重试上下文
     */
    void onRetry(RetryContext ctx);

    /**
     * 组合多个回调
     */
    static RetryAfter compose(RetryAfter... callbacks) {
        return ctx -> {
            for (RetryAfter cb : callbacks) {
                if (cb != null) {
                    cb.onRetry(ctx);
                }
            }
        };
    }

    /**
     * 创建本地日志回调
     */
    static RetryAfter slf4j(Logger logger) {
        return ctx -> logger.warn(ctx.formatMessage());
    }
}
