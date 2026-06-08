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

    /** 排除关键字列表（命中后直接抛出异常，不重试） */
    private List<String> excludeKeywords;

    /** 关键字匹配模式 */
    private MatchMode matchMode = MatchMode.CONTAINS_IGNORE_CASE;

    /** 缓存的关键字匹配器 */
    private volatile KeywordMatcher matcher;

    /** 缓存的排除关键字匹配器 */
    private volatile KeywordMatcher excludeMatcher;

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

    public List<String> getExcludeKeywords() {
        return excludeKeywords;
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords;
        this.excludeMatcher = null;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
        this.matcher = null;
        this.excludeMatcher = null;
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
     * 获取缓存的排除关键字匹配器（延迟初始化）
     */
    KeywordMatcher getExcludeMatcher() {
        if (excludeMatcher == null) {
            excludeMatcher = new KeywordMatcher(excludeKeywords, matchMode);
        }
        return excludeMatcher;
    }

    /**
     * 判断异常消息是否匹配重试关键字
     *
     * <p>三段式原则：验证 → 处理 → 返回</p>
     *
     * @param message 异常消息
     * @return 是否匹配
     */
    public boolean isKeywordMatch(String message) {
        // 验证 1：useKeyword 为 false 时，无条件重试
        if (!useKeyword) {
            return true;
        }
        // 验证 2：message 或 keywords 为空时，不匹配
        if (message == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        // 处理：使用缓存的匹配器进行匹配
        return getMatcher().matches(message);
    }

    /**
     * 判断异常消息是否匹配排除关键字（命中则不应重试）
     *
     * <p>三段式原则：验证 → 处理 → 返回</p>
     *
     * @param message 异常消息
     * @return 是否匹配排除关键字
     */
    public boolean isExcludeKeywordMatch(String message) {
        // 验证：message 或 excludeKeywords 为空时，不匹配
        if (message == null || excludeKeywords == null || excludeKeywords.isEmpty()) {
            return false;
        }
        // 处理：使用缓存的排除关键字匹配器进行匹配
        return getExcludeMatcher().matches(message);
    }
}
