package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.Plugin;

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
     * 同步事件（INSERT/UPDATE/DELETE）
     */
    private String event;

    /**
     * 插件
     */
    private Plugin plugin;

    /**
     * 插件参数（旧方式，保留兼容）
     */
    private String pluginExtInfo;

    private String traceId;

    /**
     * 是否启用字段解析器
     */
    private boolean enableSchemaResolver;

    /**
     * 是否打印trace信息
     */
    private boolean enablePrintTraceInfo;

    @Override
    public ConnectorInstance getTargetConnectorInstance() {
        return targetConnectorInstance;
    }

    public void setTargetConnectorInstance(ConnectorInstance targetConnectorInstance) {
        this.targetConnectorInstance = targetConnectorInstance;
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
    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

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
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
