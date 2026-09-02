package org.dbsyncer.common.config.props;

import org.dbsyncer.common.retry.TerminationMode;
import org.dbsyncer.common.util.MatchMode;

import java.io.Serializable;
import java.util.List;

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
}
