package org.dbsyncer.common.config.props;

import java.io.Serializable;

/**
 * 基础设置（原 SystemConfig 扁平字段组织成子对象）。
 * S1：全部包装类型。
 */
public class BasicProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer expireDataDays;
    private Integer expireLogDays;
    private Integer refreshIntervalSeconds;
    private Boolean enableStorageWriteFail;
    private Integer maxStorageErrorLength;
    private Boolean enableCDN;
    private Boolean enableWatermark;
    private String watermark;
    private Boolean enableSchemaResolver;
    private Boolean enablePrintTraceInfo;

    public Integer getExpireDataDays() {
        return expireDataDays;
    }

    public void setExpireDataDays(Integer expireDataDays) {
        this.expireDataDays = expireDataDays;
    }

    public Integer getExpireLogDays() {
        return expireLogDays;
    }

    public void setExpireLogDays(Integer expireLogDays) {
        this.expireLogDays = expireLogDays;
    }

    public Integer getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public Boolean getEnableStorageWriteFail() {
        return enableStorageWriteFail;
    }

    public void setEnableStorageWriteFail(Boolean enableStorageWriteFail) {
        this.enableStorageWriteFail = enableStorageWriteFail;
    }

    public Integer getMaxStorageErrorLength() {
        return maxStorageErrorLength;
    }

    public void setMaxStorageErrorLength(Integer maxStorageErrorLength) {
        this.maxStorageErrorLength = maxStorageErrorLength;
    }

    public Boolean getEnableCDN() {
        return enableCDN;
    }

    public void setEnableCDN(Boolean enableCDN) {
        this.enableCDN = enableCDN;
    }

    public Boolean getEnableWatermark() {
        return enableWatermark;
    }

    public void setEnableWatermark(Boolean enableWatermark) {
        this.enableWatermark = enableWatermark;
    }

    public String getWatermark() {
        return watermark;
    }

    public void setWatermark(String watermark) {
        this.watermark = watermark;
    }

    public Boolean getEnableSchemaResolver() {
        return enableSchemaResolver;
    }

    public void setEnableSchemaResolver(Boolean enableSchemaResolver) {
        this.enableSchemaResolver = enableSchemaResolver;
    }

    public Boolean getEnablePrintTraceInfo() {
        return enablePrintTraceInfo;
    }

    public void setEnablePrintTraceInfo(Boolean enablePrintTraceInfo) {
        this.enablePrintTraceInfo = enablePrintTraceInfo;
    }
}
