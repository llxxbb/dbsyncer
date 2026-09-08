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
 * S1：全部包装类型。
 */
public class RetryProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private Boolean disable;
    private Integer maxAttempts;
    private Long initialIntervalMs;
    private Long maxIntervalMs;
    private Double multiplier;
    private Long maxDurationMs;
    private TerminationMode terminationMode;
    private Boolean useKeyword;
    private MatchMode matchMode;
    private List<String> keywords;
    private List<String> excludeKeywords;

    /** 任务级重试策略覆盖（key 为任务 ID） */
    private Map<String, RetryPolicy> task = new HashMap<>();

    public Boolean getDisable() {
        return disable;
    }

    public void setDisable(Boolean disable) {
        this.disable = disable;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Long getInitialIntervalMs() {
        return initialIntervalMs;
    }

    public void setInitialIntervalMs(Long initialIntervalMs) {
        this.initialIntervalMs = initialIntervalMs;
    }

    public Long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(Long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public Long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(Long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public TerminationMode getTerminationMode() {
        return terminationMode;
    }

    public void setTerminationMode(TerminationMode terminationMode) {
        this.terminationMode = terminationMode;
    }

    public Boolean getUseKeyword() {
        return useKeyword;
    }

    public void setUseKeyword(Boolean useKeyword) {
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
        this.disable = env.getProperty(ConfigKeys.RETRY_DISABLE, Boolean.class);
        this.maxAttempts = env.getProperty(ConfigKeys.RETRY_MAX_ATTEMPTS, Integer.class);
        this.initialIntervalMs = env.getProperty(ConfigKeys.RETRY_INITIAL_INTERVAL_MS, Long.class);
        this.maxIntervalMs = env.getProperty(ConfigKeys.RETRY_MAX_INTERVAL_MS, Long.class);
        this.multiplier = env.getProperty(ConfigKeys.RETRY_MULTIPLIER, Double.class);
        this.maxDurationMs = env.getProperty(ConfigKeys.RETRY_MAX_DURATION_MS, Long.class);
        this.useKeyword = env.getProperty(ConfigKeys.RETRY_USE_KEYWORD, Boolean.class);
    }

    /**
     * 将 RetryProps 转换为全局 RetryPolicy。
     */
    public RetryPolicy toGlobalPolicy() {
        RetryPolicy policy = new RetryPolicy();
        if (disable != null) policy.setDisable(disable);
        if (maxAttempts != null) policy.setMaxAttempts(maxAttempts);
        if (initialIntervalMs != null) policy.setInitialIntervalMs(initialIntervalMs);
        if (maxIntervalMs != null) policy.setMaxIntervalMs(maxIntervalMs);
        if (multiplier != null) policy.setMultiplier(multiplier);
        if (maxDurationMs != null) policy.setMaxDurationMs(maxDurationMs);
        if (terminationMode != null) policy.setTerminationMode(terminationMode);
        if (useKeyword != null) policy.setUseKeyword(useKeyword);
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
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (maxAttempts != null && maxAttempts < 1) {
            errors.add("最大重试次数必须 >= 1");
        }
        if (initialIntervalMs != null && initialIntervalMs < 0) {
            errors.add("初始重试间隔不能 < 0");
        }
        if (maxIntervalMs != null && maxIntervalMs < 0) {
            errors.add("最大重试间隔不能 < 0");
        }
        if (multiplier != null && multiplier < 1.0) {
            errors.add("退避倍数必须 >= 1.0");
        }
        if (maxDurationMs != null && maxDurationMs < 0) {
            errors.add("重试总时长不能 < 0（0 表示不限制）");
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
