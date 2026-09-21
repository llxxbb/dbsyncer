package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 基础设置（原 SystemConfig 扁平字段组织成子对象）。
 * 标量不装箱（ADR-0029 实施修订 R1 / D1）：类型默认值就是有效值，运行期不出现 null。
 */
public class BasicProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    private int expireDataDays = 7;
    private int expireLogDays = 30;
    private int refreshIntervalSeconds = 5;
    private boolean enableStorageWriteFail = true;
    private int maxStorageErrorLength = 2048;
    private boolean enableCDN = false;
    private boolean enableWatermark = false;
    private String watermark;
    private boolean enableSchemaResolver = true;
    private boolean enablePrintTraceInfo = false;

    /** 是否重置管理员密码（功能开关，非基础设施配置） */
    private boolean resetPwd = false;

    public int getExpireDataDays() {
        return expireDataDays;
    }

    public void setExpireDataDays(int expireDataDays) {
        this.expireDataDays = expireDataDays;
    }

    public int getExpireLogDays() {
        return expireLogDays;
    }

    public void setExpireLogDays(int expireLogDays) {
        this.expireLogDays = expireLogDays;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public boolean getEnableStorageWriteFail() {
        return enableStorageWriteFail;
    }

    public void setEnableStorageWriteFail(boolean enableStorageWriteFail) {
        this.enableStorageWriteFail = enableStorageWriteFail;
    }

    public int getMaxStorageErrorLength() {
        return maxStorageErrorLength;
    }

    public void setMaxStorageErrorLength(int maxStorageErrorLength) {
        this.maxStorageErrorLength = maxStorageErrorLength;
    }

    public boolean getEnableCDN() {
        return enableCDN;
    }

    public void setEnableCDN(boolean enableCDN) {
        this.enableCDN = enableCDN;
    }

    public boolean getEnableWatermark() {
        return enableWatermark;
    }

    public void setEnableWatermark(boolean enableWatermark) {
        this.enableWatermark = enableWatermark;
    }

    public String getWatermark() {
        return watermark;
    }

    public void setWatermark(String watermark) {
        this.watermark = watermark;
    }

    public boolean getEnableSchemaResolver() {
        return enableSchemaResolver;
    }

    public void setEnableSchemaResolver(boolean enableSchemaResolver) {
        this.enableSchemaResolver = enableSchemaResolver;
    }

    public boolean getEnablePrintTraceInfo() {
        return enablePrintTraceInfo;
    }

    public void setEnablePrintTraceInfo(boolean enablePrintTraceInfo) {
        this.enablePrintTraceInfo = enablePrintTraceInfo;
    }

    public boolean getResetPwd() {
        return resetPwd;
    }

    public void setResetPwd(boolean resetPwd) {
        this.resetPwd = resetPwd;
    }

    /**
     * 从 Spring Environment 加载。
     * <p>
     * 一律使用三参 {@code getProperty(key, type, defaultValue)}：两参会在 key 缺失时返回 null，
     * 赋给基本类型直接 NPE（ADR-0029 R3）。默认值取字段当前值，即代码里声明的产品默认值，
     * 因此 application.properties 未列出的项不会被抹掉。
     */
    @Override
    public void loadFromEnvironment(Environment env) {
        this.expireDataDays = env.getProperty(ConfigKeys.BASIC_EXPIRE_DATA_DAYS, Integer.class, expireDataDays);
        this.expireLogDays = env.getProperty(ConfigKeys.BASIC_EXPIRE_LOG_DAYS, Integer.class, expireLogDays);
        this.refreshIntervalSeconds = env.getProperty(ConfigKeys.BASIC_REFRESH_INTERVAL_SECONDS, Integer.class, refreshIntervalSeconds);
        this.enableStorageWriteFail = env.getProperty(ConfigKeys.BASIC_ENABLE_STORAGE_WRITE_FAIL, Boolean.class, enableStorageWriteFail);
        this.maxStorageErrorLength = env.getProperty(ConfigKeys.BASIC_MAX_STORAGE_ERROR_LENGTH, Integer.class, maxStorageErrorLength);
        this.enableCDN = env.getProperty(ConfigKeys.BASIC_ENABLE_CDN, Boolean.class, enableCDN);
        this.enableWatermark = env.getProperty(ConfigKeys.BASIC_ENABLE_WATERMARK, Boolean.class, enableWatermark);
        this.watermark = env.getProperty(ConfigKeys.BASIC_WATERMARK, String.class, watermark);
        this.enableSchemaResolver = env.getProperty(ConfigKeys.BASIC_ENABLE_SCHEMA_RESOLVER, Boolean.class, enableSchemaResolver);
        this.enablePrintTraceInfo = env.getProperty(ConfigKeys.BASIC_ENABLE_PRINT_TRACE_INFO, Boolean.class, enablePrintTraceInfo);
        this.resetPwd = env.getProperty(ConfigKeys.BASIC_RESET_PWD, Boolean.class, resetPwd);
    }

    /**
     * 校验基础设置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (expireDataDays < 1) {
            errors.add("同步数据过期时间必须 >= 1 天");
        }
        if (expireLogDays < 1) {
            errors.add("系统日志过期时间必须 >= 1 天");
        }
        if (refreshIntervalSeconds < 1) {
            errors.add("刷新间隔必须 >= 1 秒");
        }
        if (maxStorageErrorLength < 100) {
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
