package org.dbsyncer.common.retry;

import java.util.List;

/**
 * 重试策略配置类
 * 支持关键字匹配、指数退避、最大间隔等配置
 */
public class RetryPolicy {

    /** 是否禁用重试 */
    private boolean disable = false;

    /** 最大尝试次数（含首次） */
    private int maxAttempts = 10;

    /** 初始等待间隔（毫秒） */
    private long initialIntervalMs = 1000L;

    /** 退避倍增系数 */
    private double multiplier = 2.0;

    /** 最大等待间隔（毫秒） */
    private long maxIntervalMs = 600000L;

    /** 重试总耗时上限（毫秒），0 表示不限制 */
    private long maxDurationMs = 3600000L;

    /** 终止模式 */
    private TerminationMode terminationMode = TerminationMode.WHICHEVER_FIRST;

    /** 是否启用关键字匹配 */
    private boolean useKeyword = false;

    /** 重试关键字列表 */
    private List<String> keywords;

    /** 关键字匹配模式 */
    private MatchMode matchMode = MatchMode.CONTAINS_IGNORE_CASE;

    /** 缓存的关键字匹配器 */
    private volatile KeywordMatcher matcher;

    /**
     * 关键字匹配模式
     */
    public enum MatchMode {
        /** 精确匹配 */
        EXACT,
        /** 包含匹配 */
        CONTAINS,
        /** 精确匹配（忽略大小写） */
        CASE_INSENSITIVE,
        /** 包含匹配（忽略大小写） */
        CONTAINS_IGNORE_CASE
    }

    public boolean isDisable() {
        return disable;
    }

    public void setDisable(boolean disable) {
        this.disable = disable;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialIntervalMs() {
        return initialIntervalMs;
    }

    public void setInitialIntervalMs(long initialIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public TerminationMode getTerminationMode() {
        return terminationMode;
    }

    public void setTerminationMode(TerminationMode terminationMode) {
        this.terminationMode = terminationMode;
    }

    public boolean isUseKeyword() {
        return useKeyword;
    }

    public void setUseKeyword(boolean useKeyword) {
        this.useKeyword = useKeyword;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
        this.matcher = null;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
        this.matcher = null;
    }

    /**
     * 计算第 attempt 次重试的等待间隔（指数退避）
     *
     * @param attempt 重试次数（从 0 开始）
     * @return 等待间隔（毫秒）
     */
    long calculateInterval(int attempt) {
        long interval = (long) (initialIntervalMs * Math.pow(multiplier, attempt));
        return Math.min(interval, maxIntervalMs);
    }

    /**
     * 获取缓存的关键字匹配器（延迟初始化）
     */
    KeywordMatcher getMatcher() {
        if (matcher == null) {
            matcher = new KeywordMatcher(keywords, matchMode);
        }
        return matcher;
    }

    /**
     * 判断异常消息是否匹配重试关键字
     *
     * @param message 异常消息
     * @return 是否匹配
     */
    public boolean isKeywordMatch(String message) {
        if (!useKeyword) {
            return true;
        }
        if (message == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        return getMatcher().matches(message);
    }
}
