/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */

package org.dbsyncer.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.ConfigModel;

/**
 * 系统配置
 *
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-29 20:13
 */
public class SystemConfig extends ConfigModel {

    public SystemConfig() {
        super.setType(ConfigConstant.SYSTEM);
    }

    // ===== 配置版本号（ADR-0029） =====

    /** 配置版本号，默认 0 表示未迁移 */
    private int schemaVersion;

    // ===== A 类（@JsonIgnore：不落库，每次启动时从 Environment 现读） =====

    @JsonIgnore
    private ServerProps server = new ServerProps();

    @JsonIgnore
    private StorageConnectionProps storageConn;

    @JsonIgnore
    private LoggingProps logging;

    @JsonIgnore
    private HelpdocProps helpdoc;

    @JsonIgnore
    private WorkerProps worker;

    @JsonIgnore
    private LicenseProps license;

    // ===== B 类（需重启） =====

    /** Tab 2 执行器，null=未覆盖 */
    private ExecutorProps executors;

    /** Tab 3 缓冲区，null=未覆盖 */
    private BufferProps buffers;

    // ===== C 类（立即生效） =====

    /** Tab 4 重试策略，null=未覆盖 */
    private RetryProps retry;

    /** Tab 5 通知通道，null=未覆盖 */
    private NotifyProps notify;

    /** Tab 6 错误处理，null=未覆盖 */
    private ErrorProps error;

    // ===== 基础设置（原扁平字段组织成子对象） =====

    /** Tab 1 基础设置 */
    private BasicProps basic;

    // ===== 以下为旧扁平字段（deprecated，迁移后不再使用，保留用于存量 JSON 兼容） =====

    /** @deprecated 迁移到 basic.expireDataDays */
    @Deprecated
    private int expireDataDays = 7;

    /** @deprecated 迁移到 basic.expireLogDays */
    @Deprecated
    private int expireLogDays = 30;

    /** @deprecated 迁移到 basic.refreshIntervalSeconds */
    @Deprecated
    private int refreshIntervalSeconds = 5;

    /** @deprecated 迁移到 basic.enableStorageWriteFail */
    @Deprecated
    private boolean enableStorageWriteFail = true;

    /** @deprecated 迁移到 basic.maxStorageErrorLength */
    @Deprecated
    private int maxStorageErrorLength = 2048;

    /** @deprecated 迁移到 basic.enableCDN */
    @Deprecated
    private boolean enableCDN;

    /** @deprecated 迁移到 basic.enableWatermark */
    @Deprecated
    private boolean enableWatermark;

    /** @deprecated 迁移到 basic.watermark */
    @Deprecated
    private String watermark;

    /** @deprecated 迁移到 basic.enableSchemaResolver */
    @Deprecated
    private boolean enableSchemaResolver = true;

    /** @deprecated 迁移到 basic.enablePrintTraceInfo */
    @Deprecated
    private boolean enablePrintTraceInfo;

    // ===== schemaVersion =====

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    // ===== A 类子对象 =====

    public ServerProps getServer() {
        return server;
    }

    public void setServer(ServerProps server) {
        this.server = server;
    }

    public StorageConnectionProps getStorageConn() {
        return storageConn;
    }

    public void setStorageConn(StorageConnectionProps storageConn) {
        this.storageConn = storageConn;
    }

    public LoggingProps getLogging() {
        return logging;
    }

    public void setLogging(LoggingProps logging) {
        this.logging = logging;
    }

    public HelpdocProps getHelpdoc() {
        return helpdoc;
    }

    public void setHelpdoc(HelpdocProps helpdoc) {
        this.helpdoc = helpdoc;
    }

    public WorkerProps getWorker() {
        return worker;
    }

    public void setWorker(WorkerProps worker) {
        this.worker = worker;
    }

    public LicenseProps getLicense() {
        return license;
    }

    public void setLicense(LicenseProps license) {
        this.license = license;
    }

    // ===== B 类子对象 =====

    public ExecutorProps getExecutors() {
        return executors;
    }

    public void setExecutors(ExecutorProps executors) {
        this.executors = executors;
    }

    public BufferProps getBuffers() {
        return buffers;
    }

    public void setBuffers(BufferProps buffers) {
        this.buffers = buffers;
    }

    // ===== C 类子对象 =====

    public RetryProps getRetry() {
        return retry;
    }

    public void setRetry(RetryProps retry) {
        this.retry = retry;
    }

    public NotifyProps getNotify() {
        return notify;
    }

    public void setNotify(NotifyProps notify) {
        this.notify = notify;
    }

    public ErrorProps getError() {
        return error;
    }

    public void setError(ErrorProps error) {
        this.error = error;
    }

    // ===== BasicProps =====

    public BasicProps getBasic() {
        return basic;
    }

    public void setBasic(BasicProps basic) {
        this.basic = basic;
    }

    // ===== 旧扁平字段 getter/setter（deprecated） =====

    /** @deprecated 使用 basic.expireDataDays */
    @Deprecated
    public int getExpireDataDays() {
        return expireDataDays;
    }

    /** @deprecated 使用 basic.expireDataDays */
    @Deprecated
    public void setExpireDataDays(int expireDataDays) {
        this.expireDataDays = expireDataDays;
    }

    /** @deprecated 使用 basic.expireLogDays */
    @Deprecated
    public int getExpireLogDays() {
        return expireLogDays;
    }

    /** @deprecated 使用 basic.expireLogDays */
    @Deprecated
    public void setExpireLogDays(int expireLogDays) {
        this.expireLogDays = expireLogDays;
    }

    /** @deprecated 使用 basic.refreshIntervalSeconds */
    @Deprecated
    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    /** @deprecated 使用 basic.refreshIntervalSeconds */
    @Deprecated
    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    /** @deprecated 使用 basic.enableStorageWriteFail */
    @Deprecated
    public boolean isEnableStorageWriteFail() {
        return enableStorageWriteFail;
    }

    /** @deprecated 使用 basic.enableStorageWriteFail */
    @Deprecated
    public void setEnableStorageWriteFail(boolean enableStorageWriteFail) {
        this.enableStorageWriteFail = enableStorageWriteFail;
    }

    /** @deprecated 使用 basic.maxStorageErrorLength */
    @Deprecated
    public int getMaxStorageErrorLength() {
        return maxStorageErrorLength;
    }

    /** @deprecated 使用 basic.maxStorageErrorLength */
    @Deprecated
    public void setMaxStorageErrorLength(int maxStorageErrorLength) {
        this.maxStorageErrorLength = maxStorageErrorLength;
    }

    /** @deprecated 使用 basic.enableCDN */
    @Deprecated
    public boolean isEnableCDN() {
        return enableCDN;
    }

    /** @deprecated 使用 basic.enableCDN */
    @Deprecated
    public void setEnableCDN(boolean enableCDN) {
        this.enableCDN = enableCDN;
    }

    /** @deprecated 使用 basic.enableWatermark */
    @Deprecated
    public boolean isEnableWatermark() {
        return enableWatermark;
    }

    /** @deprecated 使用 basic.enableWatermark */
    @Deprecated
    public void setEnableWatermark(boolean enableWatermark) {
        this.enableWatermark = enableWatermark;
    }

    /** @deprecated 使用 basic.watermark */
    @Deprecated
    public String getWatermark() {
        return watermark;
    }

    /** @deprecated 使用 basic.watermark */
    @Deprecated
    public void setWatermark(String watermark) {
        this.watermark = watermark;
    }

    /** @deprecated 使用 basic.enableSchemaResolver */
    @Deprecated
    public boolean isEnableSchemaResolver() {
        return enableSchemaResolver;
    }

    /** @deprecated 使用 basic.enableSchemaResolver */
    @Deprecated
    public void setEnableSchemaResolver(boolean enableSchemaResolver) {
        this.enableSchemaResolver = enableSchemaResolver;
    }

    /** @deprecated 使用 basic.enablePrintTraceInfo */
    @Deprecated
    public boolean isEnablePrintTraceInfo() {
        return enablePrintTraceInfo;
    }

    /** @deprecated 使用 basic.enablePrintTraceInfo */
    @Deprecated
    public void setEnablePrintTraceInfo(boolean enablePrintTraceInfo) {
        this.enablePrintTraceInfo = enablePrintTraceInfo;
    }
}
