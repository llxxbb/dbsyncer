package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础设置（原 SystemConfig 扁平字段组织成子对象）。
 * S1：全部包装类型。
 */
public class BasicProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private Integer expireDataDays = 7;
    private Integer expireLogDays = 30;
    private Integer refreshIntervalSeconds = 5;
    private Boolean enableStorageWriteFail = true;
    private Integer maxStorageErrorLength = 2048;
    private Boolean enableCDN = false;
    private Boolean enableWatermark = false;
    private String watermark;
    private Boolean enableSchemaResolver = true;
    private Boolean enablePrintTraceInfo = false;

    /** 是否重置管理员密码（功能开关，非基础设施配置） */
    private Boolean resetPwd;

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

    public Boolean getResetPwd() {
        return resetPwd;
    }

    public void setResetPwd(Boolean resetPwd) {
        this.resetPwd = resetPwd;
    }

    public void loadFromEnvironment(Environment env) {
        this.expireDataDays = env.getProperty(ConfigKeys.BASIC_EXPIRE_DATA_DAYS, Integer.class);
        this.expireLogDays = env.getProperty(ConfigKeys.BASIC_EXPIRE_LOG_DAYS, Integer.class);
        this.refreshIntervalSeconds = env.getProperty(ConfigKeys.BASIC_REFRESH_INTERVAL_SECONDS, Integer.class);
        this.enableStorageWriteFail = env.getProperty(ConfigKeys.BASIC_ENABLE_STORAGE_WRITE_FAIL, Boolean.class);
        this.maxStorageErrorLength = env.getProperty(ConfigKeys.BASIC_MAX_STORAGE_ERROR_LENGTH, Integer.class);
        this.enableCDN = env.getProperty(ConfigKeys.BASIC_ENABLE_CDN, Boolean.class);
        this.enableWatermark = env.getProperty(ConfigKeys.BASIC_ENABLE_WATERMARK, Boolean.class);
        this.watermark = env.getProperty(ConfigKeys.BASIC_WATERMARK, String.class);
        this.enableSchemaResolver = env.getProperty(ConfigKeys.BASIC_ENABLE_SCHEMA_RESOLVER, Boolean.class);
        this.enablePrintTraceInfo = env.getProperty(ConfigKeys.BASIC_ENABLE_PRINT_TRACE_INFO, Boolean.class);
        this.resetPwd = env.getProperty(ConfigKeys.BASIC_RESET_PWD, Boolean.class);
    }

    /**
     * 校验基础设置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (expireDataDays != null && expireDataDays < 1) {
            errors.add("同步数据过期时间必须 >= 1 天");
        }
        if (expireLogDays != null && expireLogDays < 1) {
            errors.add("系统日志过期时间必须 >= 1 天");
        }
        if (refreshIntervalSeconds != null && refreshIntervalSeconds < 1) {
            errors.add("刷新间隔必须 >= 1 秒");
        }
        if (maxStorageErrorLength != null && maxStorageErrorLength < 100) {
            errors.add("失败日志最大长度必须 >= 100");
        }
        if (watermark != null && watermark.length() > 64) {
            errors.add("水印内容最多 64 个字符");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.BASIC_EXPIRE_DATA_DAYS, expireDataDays));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_EXPIRE_LOG_DAYS, expireLogDays));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_REFRESH_INTERVAL_SECONDS, refreshIntervalSeconds));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_ENABLE_STORAGE_WRITE_FAIL, enableStorageWriteFail));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_MAX_STORAGE_ERROR_LENGTH, maxStorageErrorLength));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_ENABLE_CDN, enableCDN));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_ENABLE_WATERMARK, enableWatermark));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_WATERMARK, watermark));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_ENABLE_SCHEMA_RESOLVER, enableSchemaResolver));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_ENABLE_PRINT_TRACE_INFO, enablePrintTraceInfo));
        entries.add(new ConfigEntry(ConfigKeys.BASIC_RESET_PWD, resetPwd));
        return entries;
    }
}
