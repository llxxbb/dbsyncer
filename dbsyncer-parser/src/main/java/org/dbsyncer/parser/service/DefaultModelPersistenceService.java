/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.service;

import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.ConfigModel;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.service.ModelPersistenceService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Model 持久化服务的默认实现
 * 
 * <p>实现位于 parser 层，可访问 ProfileComponent 和 ParserComponent 来完成持久化和查询操作。
 * 
 * <p>此实现作为 ADR-0019（Model 层贫血化重构）的一部分，将 Model 中的持久化逻辑
 * 转移到 Service 层，使得 Model 类可以去除对 ProfileComponent/ParserComponent 的直接依赖。
 * 
 * @author opencode
 * @version 1.0.0
 * @date 2026-06-27
 */
@Service
public class DefaultModelPersistenceService implements ModelPersistenceService {

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ParserComponent parserComponent;

    @Override
    public String save(String modelId, Object modelObj) throws Exception {
        if (modelObj == null) {
            throw new IllegalArgumentException("Model object cannot be null");
        }

        // 检查是否为 ConfigModel 类型（Meta/Mapping/TableGroup 都继承自 ConfigModel）
        if (!(modelObj instanceof ConfigModel)) {
            throw new IllegalArgumentException("Model object must extend ConfigModel, but got: " + modelObj.getClass().getSimpleName());
        }

        // TableGroup 使用专用的 TABLE 策略，与 ConfigModel 的 DEFAULT 策略不同
        if (modelObj instanceof TableGroup) {
            TableGroup tableGroup = (TableGroup) modelObj;
            if (modelId == null || modelId.isEmpty()) {
                return profileComponent.addTableGroup(tableGroup);
            } else {
                return profileComponent.editTableGroup(tableGroup);
            }
        }

        ConfigModel configModel = (ConfigModel) modelObj;
        
        // 调用 ProfileComponent 的持久化方法
        if (modelId == null || modelId.isEmpty()) {
            return profileComponent.addConfigModel(configModel);
        } else {
            return profileComponent.editConfigModel(configModel);
        }
    }

    @Override
    public Object getMapping(String mappingId) {
        if (mappingId == null || mappingId.isEmpty()) {
            return null;
        }
        return profileComponent.getMapping(mappingId);
    }

    @Override
    public Object getConnector(String connectorId) {
        if (connectorId == null || connectorId.isEmpty()) {
            return null;
        }
        return profileComponent.getConnector(connectorId);
    }

    @Override
    public List<Object> getAllTableGroups(String mappingId) {
        if (mappingId == null || mappingId.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // profileComponent.getTableGroupAll 返回 List<TableGroup>，需要转换为 List<Object>
            return new java.util.ArrayList<>(profileComponent.getTableGroupAll(mappingId));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get all table groups for mapping: " + mappingId, e);
        }
    }

    @Override
    public Object getMeta(String metaId) {
        if (metaId == null || metaId.isEmpty()) {
            return null;
        }
        return profileComponent.getMeta(metaId);
    }

    @Override
    public MetaInfo getTableMetaInfo(String connectorId, String tableName) throws Exception {
        if (connectorId == null || connectorId.isEmpty() || tableName == null || tableName.isEmpty()) {
            return null;
        }
        try {
            return parserComponent.getMetaInfo(connectorId, tableName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get table meta info for connector: "
                + connectorId + ", table: " + tableName, e);
        }
    }

    @Override
    public ConnectorConfig getConnectorConfig(String connectorId) {
        if (connectorId == null || connectorId.isEmpty()) {
            return null;
        }
        Connector connector = profileComponent.getConnector(connectorId);
        if (connector == null) {
            return null;
        }
        return connector.getConfig();
    }

    /**
     * 辅助方法：从 List<Object> 获取 List<TableGroup>
     * 
     * <p>由于 getTableGroupAll 返回的 List<TableGroup> 被转换为 List<Object>，
     * 需要通过 Object 类型还原。使用方式：
     * 
     * <pre>
     * List<Object> objs = service.getAllTableGroups(mappingId);
     * List&lt;TableGroup&gt; tableGroups = DefaultModelPersistenceService.castToListTableGroup(objs);
     * </pre>
     * 
     * @param objects 对象列表
     * @return TableGroup 列表
     */
    @SuppressWarnings("unchecked")
    public static List<TableGroup> castToListTableGroup(List<Object> objects) {
        if (objects == null || objects.isEmpty()) {
            return Collections.emptyList();
        }
        List<TableGroup> result = new java.util.ArrayList<>(objects.size());
        for (Object obj : objects) {
            if (obj instanceof TableGroup) {
                result.add((TableGroup) obj);
            }
        }
        return result;
    }

    /**
     * 辅助方法：从 Object 还原为特定类型
     * 
     * @param obj 对象
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 转换后的对象，如果类型不匹配返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T castToType(Object obj, Class<T> clazz) {
        if (obj == null) {
            return null;
        }
        if (clazz.isInstance(obj)) {
            return (T) obj;
        }
        return null;
    }
}
