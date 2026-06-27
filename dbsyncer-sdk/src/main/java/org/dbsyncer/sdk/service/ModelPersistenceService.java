/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.service;

import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.MetaInfo;

/**
 * Model 持久化服务接口
 * 
 * <p>用于承接 Model 层（Meta、Mapping、TableGroup）的持久化和查询操作，
 * 使得 Model 类可以去除对 ProfileComponent 和 ParserComponent 的直接依赖。
 * 
 * <p>设计思路:
 * <ul>
 *     <li>Model 贫血化: 模型只包含数据和业务逻辑，不包含持久化逻辑</li>
 *     <li>分层解耦: Service 层在 sdk 层定义接口，实现在 parser 层可访问 ProfileComponent 和 ParserComponent</li>
 *     <li>最小化依赖: 只暴露必要的操作接口，避免循环依赖</li>
 * </ul>
 * 
 * @author opencode
 * @version 1.0.0
 * @date 2026-06-27
 */
public interface ModelPersistenceService {

    /**
     * 保存 Model 对象到持久化存储
     * 
     * @param modelId   Model ID（可为空，新增时自动生成）
     * @param modelObj  Model 对象实例
     * @return 保存后的 ID
     * @throws Exception 持久化异常
     */
    String save(String modelId, Object modelObj) throws Exception;

    /**
     * 查询指定 Mapping 关联的 Mapping 对象
     * 
     * @param mappingId Mapping ID
     * @return Mapping 对象，如果不存在返回 null
     */
    Object getMapping(String mappingId);

    /**
     * 查询指定 Connector ID 关联的连接器配置
     * 
     * @param connectorId Connector ID
     * @return Connector 对象，如果不存在返回 null
     */
    Object getConnector(String connectorId);

    /**
     * 查询指定 Mapping 关联的所有 TableGroup 列表
     * 
     * @param mappingId Mapping ID
     * @return TableGroup 列表（可能为空集合）
     */
    java.util.List<Object> getAllTableGroups(String mappingId);

    /**
     * 查询指定 Meta ID 关联的 Meta 对象
     * 
     * @param metaId Meta ID
     * @return Meta 对象，如果不存在返回 null
     */
    Object getMeta(String metaId);

    /**
     * 查询指定 connector 和表名的数据库元数据信息
     * 
     * @param connectorId Connector ID
     * @param tableName   表名
     * @return MetaInfo 对象，如果不存在返回 null
     * @throws Exception 查询异常
     */
    MetaInfo getTableMetaInfo(String connectorId, String tableName) throws Exception;

    /**
     * 查询指定 connector 的配置信息
     * 
     * <p>返回 ConnectorConfig 对象，包含连接信息（URL、用户名、密码等）
     * 
     * @param connectorId Connector ID
     * @return ConnectorConfig 对象
     */
    ConnectorConfig getConnectorConfig(String connectorId);
}
