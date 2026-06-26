package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/6/30 16:00
 */
public abstract class AbstractPluginContext extends AbstractBaseContext implements PluginContext, Cloneable {

    /**
     * 目标源连接实例
     */
    private ConnectorInstance targetConnectorInstance;

    /**
     * 数据源表
     */
    private String sourceTableName;

    /**
     * 目标源表
     */
    private String targetTableName;

    /**
     * 同步事件（INSERT/UPDATE/DELETE）
     */
    private String event;

    /**
     * 目标字段
     */
    private List<Field> targetFields;

    /**
     * 预计算的主键字段列表（按主键顺序）
     * 更新/删除等操作直接读取，避免每条数据重复查找
     */
    private List<Field> pkFields = Collections.emptyList();

    /**
     * 批量处理任务数
     */
    private int batchSize;

    /**
     * 是否覆盖更新
     */
    private boolean forceUpdate;

    /**
     * 是否启用字段解析器
     */
    private boolean enableSchemaResolver;

    /**
     * 是否打印trace信息
     */
    private boolean enablePrintTraceInfo;

    /**
     * 数据源数据集合
     */
    private List<Map> sourceList;

    /**
     * 目标源源数据集合
     */
    private List<Map> targetList;

    /**
     * 插件
     */
    private Plugin plugin;

    /**
     * 插件参数（旧方式，保留兼容）
     */
    private String pluginExtInfo;
    
    /**
     * 主键列表（新方式，直接 List，避免 JSON 解析）
     */
    private List<String> primaryKeys;

    /**
     * 同步数据列表（包含数据和上下文）
     */
    private List<SyncRow> syncRows;

    private String traceId;

    @Override
    public ConnectorInstance getTargetConnectorInstance() {
        return targetConnectorInstance;
    }

    public void setTargetConnectorInstance(ConnectorInstance targetConnectorInstance) {
        this.targetConnectorInstance = targetConnectorInstance;
    }

    @Override
    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    @Override
    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    @Override
    public String getEvent() {
        return event;
    }

    @Override
    public void setEvent(String event) {
        this.event = event;
    }

    @Override
    public List<Field> getTargetFields() {
        return targetFields;
    }

    public void setTargetFields(List<Field> targetFields) {
        this.targetFields = targetFields;
    }

    @Override
    public List<Field> getPkFields() {
        return pkFields;
    }

    @Override
    public void setPkFields(List<Field> pkFields) {
        this.pkFields = pkFields == null ? Collections.<Field>emptyList() : pkFields;
    }

    @Override
    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    @Override
    public boolean isEnableSchemaResolver() {
        return enableSchemaResolver;
    }

    public void setEnableSchemaResolver(boolean enableSchemaResolver) {
        this.enableSchemaResolver = enableSchemaResolver;
    }

    @Override
    public boolean isEnablePrintTraceInfo() {
        return enablePrintTraceInfo;
    }

    public void setEnablePrintTraceInfo(boolean enablePrintTraceInfo) {
        this.enablePrintTraceInfo = enablePrintTraceInfo;
    }

    @Override
    public List<Map> getSourceList() {
        return sourceList;
    }

    public void setSourceList(List<Map> sourceList) {
        this.sourceList = sourceList;
    }

    @Override
    public List<Map> getTargetList() {
        return targetList;
    }

    public void setTargetList(List<Map> targetList) {
        this.targetList = targetList;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 获取主键列表
     */
    @Override
    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    @Override
    public void setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
    }

    /**
     * 获取插件参数（旧方式）
     */
    public String getPluginExtInfo() {
        return pluginExtInfo;
    }

    public void setPluginExtInfo(String pluginExtInfo) {
        this.pluginExtInfo = pluginExtInfo;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public List<SyncRow> getSyncRows() {
        return syncRows;
    }

    @Override
    public void setSyncRows(List<SyncRow> syncRows) {
        this.syncRows = syncRows;
    }
}