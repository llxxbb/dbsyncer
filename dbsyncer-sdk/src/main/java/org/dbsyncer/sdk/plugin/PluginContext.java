package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.enums.ModelEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Plugin;

import java.util.List;
import java.util.Map;

/**
 * 插件转换上下文
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/10/28 20:26
 */
public interface PluginContext extends BaseContext {

    /**
     * 获取同步主键列表（按数据库主键顺序）
     * @return 主键名称列表
     */
    List<String> getPrimaryKeys();

    /**
     * 设置同步主键列表
     * @param primaryKeys 主键名称列表
     */
    void setPrimaryKeys(List<String> primaryKeys);

    /**
     * 获取预计算的主键字段列表（按主键顺序）
     * 初始化阶段由 ParserComponentImpl 根据 primaryKeys + targetFields 解析，
     * 后续 update/delete/upsert 等高频操作直接读取，避免重复查找。
     * @return 有序的主键字段列表，never null
     */
    List<Field> getPkFields();

    void setPkFields(List<Field> pkFields);

    /**
     * 获取同步方式
     *
     * @return
     */
    ModelEnum getModelEnum();

    /**
     * 目标源连接实例
     */
    ConnectorInstance getTargetConnectorInstance();

    /**
     * 数据源表
     */
    String getSourceTableName();

    /**
     * 目标源表
     */
    String getTargetTableName();

    /**
     * 增量同步，事件（INSERT/UPDATE/DELETE）
     */
    String getEvent();

    void setEvent(String event);

    /**
     * 目标字段
     */
    List<Field> getTargetFields();

    /**
     * 批量处理任务数
     */
    int getBatchSize();

    /**
     * 是否覆盖更新
     */
    boolean isForceUpdate();

    /**
     * 是否启用字段解析器
     */
    boolean isEnableSchemaResolver();

    /**
     * 是否打印trace信息
     */
    boolean isEnablePrintTraceInfo();

    /**
     * 数据源数据集合
     */
    List<Map> getSourceList();

    /**
     * 目标源源数据集合
     */
    List<Map> getTargetList();

    void setTargetList(List<Map> targetList);

    /**
     * 获取插件
     *
     * @return
     */
    Plugin getPlugin();

    /**
     * 获取插件参数
     *
     * @return
     */
    String getPluginExtInfo();

    /**
     * 获取TraceId
     *
     * @return
     */
    String getTraceId();

    /**
     * 浅拷贝
     *
     * @return
     * @throws CloneNotSupportedException
     */
    Object clone() throws CloneNotSupportedException;
}