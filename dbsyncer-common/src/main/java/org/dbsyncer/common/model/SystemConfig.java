/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */

package org.dbsyncer.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.config.ConfigEncryptor;
import org.dbsyncer.common.config.AppProps;
import org.dbsyncer.common.config.SensitiveField;
import org.dbsyncer.common.config.SystemTabEnum;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.constant.ConfigConstant;

import java.util.Map;

/**
 * 系统配置。
 * <p>
 * 从 dbsyncer-parser 迁移到 dbsyncer-common，使底层模块可直接访问配置。
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

    // ===== A 类（@JsonIgnore，不落库，启动时从 Environment 读取） =====

    @JsonIgnore
    private ServerProps server;

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

    /** 加密密钥配置（A 类：环境敏感，只读展示） */
    @JsonIgnore
    private EncryptKeyProps encryptKey;

    // ===== 模板引擎（C 类：立即生效） =====

    private TemplateProps template;

    // ===== B 类（热重载） =====

    /** Tab 2 执行器，null=未覆盖 */
    private ExecutorProps executors;

    /** Tab 3 缓冲区，null=未覆盖 */
    private BufferProps buffers;

    // ===== C 类（立即生效） =====

    /** 监控定时任务，null=未覆盖 */
    private MonitorProps monitor;

    /** Tab 4 重试策略，null=未覆盖 */
    private RetryProps retry;

    /** Tab 5 通知通道，null=未覆盖 */
    private NotifyProps notify;

    /**
     * 分组通知配置（ADR-0035）
     * <p>
     * key = projectGroupId → 该分组的独立配置
     * key = "__unassigned__" → 未分配分组的独立配置
     */
    private Map<String, NotifyProps> groupNotifyConfigs;

    /** Tab 6 错误处理，null=未覆盖 */
    private ErrorProps error;

    /** SQL Server 增量同步配置，null=未覆盖 */
    private SqlServerCTProps sqlserverCT;

    // ===== 基础设置（原扁平字段组织成子对象） =====

    /** Tab 1 基础设置 */
    private BasicProps basic;

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

    public EncryptKeyProps getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(EncryptKeyProps encryptKey) {
        this.encryptKey = encryptKey;
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

    public MonitorProps getMonitor() {
        return monitor;
    }

    public void setMonitor(MonitorProps monitor) {
        this.monitor = monitor;
    }

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

    public Map<String, NotifyProps> getGroupNotifyConfigs() {
        return groupNotifyConfigs;
    }

    public void setGroupNotifyConfigs(Map<String, NotifyProps> groupNotifyConfigs) {
        this.groupNotifyConfigs = groupNotifyConfigs;
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

    // ===== TemplateProps =====

    public TemplateProps getTemplate() {
        return template;
    }

    public void setTemplate(TemplateProps template) {
        this.template = template;
    }

    // ===== SqlServerCTProps =====

    public SqlServerCTProps getSqlserverCT() {
        return sqlserverCT;
    }

    public void setSqlserverCT(SqlServerCTProps sqlserverCT) {
        this.sqlserverCT = sqlserverCT;
    }

    // ===== 敏感字段加解密 =====

    /**
     * 对所有敏感字段执行加密（存储前调用）。
     *
     * @param encryptor 加密器
     */
    public void encryptSensitiveFields(ConfigEncryptor encryptor) {
        for (SystemTabEnum tab : SystemTabEnum.values()) {
            AppProps props = tab.getProps(this);
            if (props != null) {
                for (SensitiveField field : props.getSensitiveFields()) {
                    field.encrypt(encryptor);
                }
            }
        }
    }

    /**
     * 对所有敏感字段执行解密（存储后调用）。
     * <p>
     * 兼容明文旧数据：解密失败时保留原值。
     *
     * @param encryptor 加密器
     */
    public void decryptSensitiveFields(ConfigEncryptor encryptor) {
        for (SystemTabEnum tab : SystemTabEnum.values()) {
            AppProps props = tab.getProps(this);
            if (props != null) {
                for (SensitiveField field : props.getSensitiveFields()) {
                    field.decrypt(encryptor);
                }
            }
        }
    }

    /**
     * 恢复指定 Tab 的被掩码敏感字段。
     * 用于 editTab 场景：只编辑单个 Tab，无需遍历所有 Tab。
     * <p>
     * 委托给 {@link AppProps#restoreMaskedFields} 通用实现。
     *
     * @param tabEnum 当前编辑的 Tab
     * @param incoming 前端传入的 Tab 级 Props（可能包含掩码值）
     * @param existing 数据库中的现有 SystemConfig（明文）
     */
    public void restoreMaskedSensitiveFields(SystemTabEnum tabEnum,
                                             AppProps incoming,
                                             SystemConfig existing) {
        if (incoming == null || existing == null) {
            return;
        }
        AppProps exProps = tabEnum.getProps(existing);
        AppProps.restoreMaskedFields(incoming, exProps);
    }
}
