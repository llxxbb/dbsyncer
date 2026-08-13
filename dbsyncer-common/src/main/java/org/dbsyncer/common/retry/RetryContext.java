package org.dbsyncer.common.retry;

/**
 * 重试上下文
 * 封装重试触发后的上下文信息，供 RetryAfter 回调使用
 */
public class RetryContext {

    private final String mappingId;
    private final int attempt;
    private final long intervalMs;
    private final String matchedKeyword;
    private final Exception exception;

    public RetryContext(String mappingId, int attempt, long intervalMs, String matchedKeyword, Exception exception) {
        this.mappingId = mappingId;
        this.attempt = attempt;
        this.intervalMs = intervalMs;
        this.matchedKeyword = matchedKeyword;
        this.exception = exception;
    }

    public String getMappingId() {
        return mappingId;
    }

    public int getAttempt() {
        return attempt;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public String getMatchedKeyword() {
        return matchedKeyword;
    }

    public Exception getException() {
        return exception;
    }

    public String formatMessage() {
        String keyword = matchedKeyword != null ? matchedKeyword : "无条件";
        return String.format("**触发重试**: mappingId=%s, 第%d次, 间隔=%dms, 关键字=%s ,异常=%s",
                mappingId, attempt, intervalMs, keyword, exception.getMessage());
    }
}
