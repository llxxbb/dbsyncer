/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.service;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Filter;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.service.ModelPersistenceService;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TableGroup 模型服务
 * 
 * <p>
 * 封装 TableGroup 的持久化和业务逻辑，替代原来 TableGroup 类中对 ProfileComponent/ParserComponent
 * 的直接依赖。
 * 
 * <p>
 * ADR-0019: Model 层贫血化重构的一部分，将 TableGroup 的持久化逻辑从 Model 层转移到 Service 层。
 * 
 * @author opencode
 * @version 1.0.0
 * @date 2026-06-27
 */
@Service
public class TableGroupModelService {

    @Resource
    private ModelPersistenceService persistenceService;

    @Resource
    private ConnectorFactory connectorFactory;

    /**
     * 初始化 TableGroup
     * 
     * @param tableGroup 需要初始化的 TableGroup 对象
     * @param mapping    关联的 Mapping 对象
     * @throws Exception 初始化异常
     */
    public void initTableGroup(TableGroup tableGroup, Mapping mapping) throws Exception {
        if (tableGroup.isInit)
            return;

        initCommand(tableGroup, mapping);

        // 如果 estimatedTotal 为 0，则设置为 sourceTable.getCount()
        if (tableGroup.getEstimatedTotal() == 0
                && tableGroup.getSourceTable() != null
                && tableGroup.getSourceTable().getCount() > 0) {
            tableGroup.setEstimatedTotal(tableGroup.getSourceTable().getCount());
            persistenceService.save(tableGroup.getId(), tableGroup);
        }
        tableGroup.isInit = true;
    }

    /**
     * 初始化 TableGroup 的执行命令（SQL 等）
     * 
     * @param tableGroup 需要初始化命令的 TableGroup 对象
     * @param mapping    关联的 Mapping 对象
     * @throws Exception 初始化异常
     */
    public void initCommand(TableGroup tableGroup, Mapping mapping) throws Exception {
        ConnectorConfig sConnConfig = persistenceService.getConnectorConfig(mapping.getSourceConnectorId());
        ConnectorConfig tConnConfig = persistenceService.getConnectorConfig(mapping.getTargetConnectorId());

        // 关键修复：fieldMapping 不能为空
        if (CollectionUtils.isEmpty(tableGroup.getFieldMapping())) {
            throw new SdkException(String.format(
                    "字段映射为空！表组ID=%s, 表组名=%s, 源表=%s, 目标表=%s。请在添加或编辑表组时配置字段映射关系。",
                    tableGroup.getId(), tableGroup.getName(),
                    tableGroup.getSourceTable() != null ? tableGroup.getSourceTable().getName() : "null",
                    tableGroup.getTargetTable() != null ? tableGroup.getTargetTable().getName() : "null"));
        }

        // 基于 fieldMapping 构建同步用的源/目标表
        List<Field> sourceFields = tableGroup.getSyncSourceFields();
        List<Field> targetFields = tableGroup.getSyncTargetFields();
        MetaInfo sTable = tableGroup.getSourceTable().clone().setColumn(new ArrayList<>(sourceFields));

        MetaInfo tTable = tableGroup.getTargetTable().clone()
                .setColumn(new ArrayList<>(targetFields))
                .setPrimaryKeys(tableGroup.getTargetTable().getPrimaryKeys());

        // 如果 tableGroup.getFilter() 为空使用 mapping.getFilter()
        List<Filter> filters = CollectionUtils.isEmpty(tableGroup.getFilter()) ? mapping.getFilter()
                : tableGroup.getFilter();

        final CommandConfig sourceConfig = new CommandConfig(sConnConfig.getConnectorType(), sTable,
                connectorFactory.connect(sConnConfig, false), filters);
        final CommandConfig targetConfig = new CommandConfig(tConnConfig.getConnectorType(), tTable,
                connectorFactory.connect(tConnConfig, false), null);

        // 预构建字段列表 SQL 片段并缓存
        String fieldListSql = tableGroup.getCachedFieldListSql();
        if (StringUtil.isBlank(fieldListSql)) {
            ConnectorService connectorService = connectorFactory.getConnectorService(sConnConfig);
            if (connectorService instanceof AbstractDatabaseConnector) {
                AbstractDatabaseConnector dbConnector = (AbstractDatabaseConnector) connectorService;
                List<String> fieldList = sTable.getColumn().stream().map(Field::getName).collect(Collectors.toList());
                fieldListSql = dbConnector.sqlTemplate.buildQuotedStringList(fieldList);
                tableGroup.setCachedFieldListSql(fieldListSql);
                tableGroup.setCachedPrimaryKeys(dbConnector.sqlTemplate.buildQuotedStringList(sTable.getPrimaryKeys()));
            }
        }
        sourceConfig.setCachedFieldListSql(fieldListSql);
        sourceConfig.setCachedPrimaryKeys(tableGroup.getCachedPrimaryKeys());

        Map<String, String> commandGenerated = connectorFactory.getCommand(sourceConfig, targetConfig);
        tableGroup.setCommand(commandGenerated);
    }

    /**
     * 从源表元数据初始化目标表主键配置
     * 
     * @param tableGroup  需要初始化主键的 TableGroup 对象
     * @param connectorId 源连接器 ID
     * @param tableName   源表名
     * @return 是否成功设置主键配置
     */
    public boolean initTargetTablePKFromSource(TableGroup tableGroup, String connectorId, String tableName) {
        if (!CollectionUtils.isEmpty(tableGroup.getTargetTable().getPrimaryKeys())) {
            return true;
        }
        try {
            MetaInfo metaInfo = persistenceService.getTableMetaInfo(connectorId, tableName);
            if (metaInfo != null && !CollectionUtils.isEmpty(metaInfo.getPrimaryKeys())) {
                tableGroup.getTargetTable().setPrimaryKeys(metaInfo.getPrimaryKeys());
                return true;
            }
        } catch (Exception e) {
            // 静默失败
        }
        return false;
    }

    /**
     * 创建新的 TableGroup 对象
     * 
     * @param mappingId   关联的 Mapping ID
     * @param sourceTable 源表名
     * @return 创建后已持久化的 TableGroup 对象
     */
    public TableGroup create(String mappingId, String sourceTable) {
        TableGroup tableGroup = new TableGroup();
        tableGroup.setName(mappingId + ":" + sourceTable);
        tableGroup.currentVersion = TableGroup.Version;
        tableGroup.setMappingId(mappingId);
        return tableGroup;
    }

    /**
     * 复制 TableGroup
     * 
     * @param tableGroup        需要复制的 TableGroup 对象
     * @param newMappingId      新的 Mapping ID
     * @param snowflakeIdWorker ID 生成器
     * @return 复制后的新 TableGroup 对象
     * @throws Exception 异常
     */
    public TableGroup copy(TableGroup tableGroup, String newMappingId,
            SnowflakeIdWorker snowflakeIdWorker) throws Exception {
        String tableGroupJson = JsonUtil.objToJson(tableGroup);
        TableGroup newTableGroup = JsonUtil.jsonToObj(tableGroupJson, TableGroup.class);
        newTableGroup.clear();
        newTableGroup.setId(String.valueOf(snowflakeIdWorker.nextId()));
        newTableGroup.setMappingId(newMappingId);
        newTableGroup.setName(newMappingId + ":" + newTableGroup.getSourceTable().getName());

        persistenceService.save(null, newTableGroup);
        return newTableGroup;
    }

    /**
     * 版本迁移
     * 
     * @param tableGroup 需要迁移版本的 TableGroup 对象
     * @param mapping    关联的 Mapping 对象
     * @throws Exception 异常
     */
    public void migrateVersion(TableGroup tableGroup, Mapping mapping) throws Exception {
        if (tableGroup.currentVersion == TableGroup.Version)
            return;

        if (tableGroup.currentVersion < TableGroup.Version) {
            if (tableGroup.getSourceTable() != null && mapping != null) {
                String connectorId = mapping.getSourceConnectorId();
                String tableName = tableGroup.getSourceTable().getName();

                // 修复 sourceTable.primaryKeys 缺失（ADR-0017 前旧数据没有该字段）
                if (CollectionUtils.isEmpty(tableGroup.getSourceTable().getPrimaryKeys())) {
                    MetaInfo freshMeta = persistenceService.getTableMetaInfo(connectorId, tableName);
                    tableGroup.setSourceTable(freshMeta);
                }

                // Version 6→7: 修复 targetTable.primaryKeys（targetTablePK 字段已移除）
                if (tableGroup.getTargetTable() != null
                        && CollectionUtils.isEmpty(tableGroup.getTargetTable().getPrimaryKeys())) {
                    MetaInfo targetMeta = persistenceService.getTableMetaInfo(
                            mapping.getTargetConnectorId(), tableGroup.getTargetTable().getName());
                    tableGroup.setTargetTable(targetMeta);
                }
            }
        }

        tableGroup.currentVersion = TableGroup.Version;
        persistenceService.save(tableGroup.getId(), tableGroup);
    }
}
