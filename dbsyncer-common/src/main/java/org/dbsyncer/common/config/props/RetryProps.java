package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.common.retry.TerminationMode;
import org.dbsyncer.common.util.MatchMode;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 4 重试策略（字段对齐 RetryPolicy）。
 * 标量不装箱（ADR-0029 实施修订 R1 / D1）：类型默认值就是有效值，运行期不出现 null。
 */
public class RetryProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private boolean disable = false;
    private int maxAttempts = 3;
    private long initialIntervalMs = 1000L;
    private long maxIntervalMs = 60000L;
    private double multiplier = 2.0;
    private long maxDurationMs = 20000L;
    private TerminationMode terminationMode;
    private boolean useKeyword = false;
    private MatchMode matchMode;
    private List<String> keywords;
    private List<String> excludeKeywords;

    /** 任务级重试策略覆盖（key 为任务 ID） */
    private Map<String, RetryPolicy> task = new HashMap<>();

    public boolean getDisable() {
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

    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
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

    public boolean getUseKeyword() {
        return useKeyword;
    }

    public void setUseKeyword(boolean useKeyword) {
        this.useKeyword = useKeyword;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode matchMode) {
        this.matchMode = matchMode;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords;
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords;
    }

    public Map<String, RetryPolicy> getTask() {
        return task;
    }

    public void setTask(Map<String, RetryPolicy> task) {
        this.task = task;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        this.disable = env.getProperty(ConfigKeys.RETRY_DISABLE, Boolean.class, disable);
        this.maxAttempts = env.getProperty(ConfigKeys.RETRY_MAX_ATTEMPTS, Integer.class, maxAttempts);
        this.initialIntervalMs = env.getProperty(ConfigKeys.RETRY_INITIAL_INTERVAL_MS, Long.class, initialIntervalMs);
        this.maxIntervalMs = env.getProperty(ConfigKeys.RETRY_MAX_INTERVAL_MS, Long.class, maxIntervalMs);
        this.multiplier = env.getProperty(ConfigKeys.RETRY_MULTIPLIER, Double.class, multiplier);
        this.maxDurationMs = env.getProperty(ConfigKeys.RETRY_MAX_DURATION_MS, Long.class, maxDurationMs);
        this.useKeyword = env.getProperty(ConfigKeys.RETRY_USE_KEYWORD, Boolean.class, useKeyword);
    }

    /**
     * 将 RetryProps 转换为全局 RetryPolicy。
     */
    public RetryPolicy toGlobalPolicy() {
        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(disable);
        policy.setMaxAttempts(maxAttempts);
        policy.setInitialIntervalMs(initialIntervalMs);
        policy.setMaxIntervalMs(maxIntervalMs);
        policy.setMultiplier(multiplier);
        policy.setMaxDurationMs(maxDurationMs);
        if (terminationMode != null) policy.setTerminationMode(terminationMode);
        policy.setUseKeyword(useKeyword);
        if (matchMode != null) policy.setMatchMode(matchMode);
        if (keywords != null) policy.setKeywords(keywords);
        if (excludeKeywords != null) policy.setExcludeKeywords(excludeKeywords);
        return policy;
    }

    /**
     * 校验重试策略配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (maxAttempts < 1) {
            errors.add("最大重试次数必须 >= 1");
        }
        if (initialIntervalMs < 0) {
            errors.add("初始重试间隔不能 < 0");
        }
        if (multiplier < 1.0) {
            errors.add("退避倍数必须 >= 1.0");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.RETRY_DISABLE, disable));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_MAX_ATTEMPTS, maxAttempts));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_INITIAL_INTERVAL_MS, initialIntervalMs));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_MAX_INTERVAL_MS, maxIntervalMs));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_MULTIPLIER, multiplier));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_MAX_DURATION_MS, maxDurationMs));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_TERMINATION_MODE, terminationMode));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_USE_KEYWORD, useKeyword));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_MATCH_MODE, matchMode));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_KEYWORDS, keywords));
        entries.add(new ConfigEntry(ConfigKeys.RETRY_EXCLUDE_KEYWORDS, excludeKeywords));
        return entries;
    }
}
