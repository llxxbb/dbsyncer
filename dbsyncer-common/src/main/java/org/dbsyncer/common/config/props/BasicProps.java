package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("basic.expire-data-days", expireDataDays));
        entries.add(new ConfigEntry("basic.expire-log-days", expireLogDays));
        entries.add(new ConfigEntry("basic.refresh-interval-seconds", refreshIntervalSeconds));
        entries.add(new ConfigEntry("basic.enable-storage-write-fail", enableStorageWriteFail));
        entries.add(new ConfigEntry("basic.max-storage-error-length", maxStorageErrorLength));
        entries.add(new ConfigEntry("basic.enable-cdn", enableCDN));
        entries.add(new ConfigEntry("basic.enable-watermark", enableWatermark));
        entries.add(new ConfigEntry("basic.watermark", watermark));
        entries.add(new ConfigEntry("basic.enable-schema-resolver", enableSchemaResolver));
        entries.add(new ConfigEntry("basic.enable-print-trace-info", enablePrintTraceInfo));
        return entries;
    }
}
