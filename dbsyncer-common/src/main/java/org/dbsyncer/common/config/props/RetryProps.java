package org.dbsyncer.common.config.props;

import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.common.retry.TerminationMode;
import org.dbsyncer.common.util.MatchMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab 4 重试策略（字段对齐 RetryPolicy）。
 * S1：全部包装类型。
 */
public class RetryProps implements Serializable {

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
        entries.add(new ConfigEntry("dbsyncer.retry.global.disable", disable));
        entries.add(new ConfigEntry("dbsyncer.retry.global.max-attempts", maxAttempts));
        entries.add(new ConfigEntry("dbsyncer.retry.global.initial-interval-ms", initialIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.retry.global.max-interval-ms", maxIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.retry.global.multiplier", multiplier));
        entries.add(new ConfigEntry("dbsyncer.retry.global.max-duration-ms", maxDurationMs));
        entries.add(new ConfigEntry("dbsyncer.retry.global.termination-mode", terminationMode));
        entries.add(new ConfigEntry("dbsyncer.retry.global.use-keyword", useKeyword));
        entries.add(new ConfigEntry("dbsyncer.retry.global.match-mode", matchMode));
        entries.add(new ConfigEntry("dbsyncer.retry.global.keywords", keywords));
        entries.add(new ConfigEntry("dbsyncer.retry.global.exclude-keywords", excludeKeywords));
        return entries;
    }
}
