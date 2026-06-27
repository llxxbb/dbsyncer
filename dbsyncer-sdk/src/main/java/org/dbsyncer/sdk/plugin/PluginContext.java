package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.enums.ModelEnum;
import org.dbsyncer.sdk.model.Plugin;

/**
 * 插件转换上下文（运行时状态，不含配置模型）
 * 配置模型通过 SyncRow.getMapping()/getTableGroup() 访问
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/10/28 20:26
 */
public interface PluginContext extends BaseContext {

    /**
     * 获取同步方式
     */
    ModelEnum getModelEnum();

    /**
     * 目标源连接实例
     */
    ConnectorInstance getTargetConnectorInstance();

    /**
     * 增量同步，事件（INSERT/UPDATE/DELETE）
     */
    String getEvent();

    void setEvent(String event);

    /**
     * 是否启用字段解析器
     */
    boolean isEnableSchemaResolver();

    /**
     * 是否打印trace信息
     */
    boolean isEnablePrintTraceInfo();

    /**
     * 获取插件
     */
    Plugin getPlugin();

    /**
     * 获取插件参数
     */
    String getPluginExtInfo();

    /**
     * 获取TraceId
     */
    String getTraceId();

    /**
     * 浅拷贝
     */
    Object clone() throws CloneNotSupportedException;
}
