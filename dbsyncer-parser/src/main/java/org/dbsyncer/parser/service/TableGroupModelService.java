/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.service;

import org.apache.commons.lang3.StringUtils;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Filter;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.common.config.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;

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
    private final MappingModelService mappingModelService;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private LogService logService;

    TableGroupModelService(MappingModelService mappingModelService) {
        this.mappingModelService = mappingModelService;
    }

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

        try {
            initCommand(tableGroup, mapping);
        } catch (Exception e) {
            String errorMsg = String.format("[初始化命令] 异常：sourceTable=%s, targetTable=%s, error=%s\nstackTrace=%s",
                    tableGroup.getSourceTable() != null ? tableGroup.getSourceTable().getName() : "null",
                    tableGroup.getTargetTable() != null ? tableGroup.getTargetTable().getName() : "null",
                    e.getMessage(),
                    ExceptionUtils.getStackTrace(e));
            logService.log(mapping, LogType.INIT, LogLevel.ERROR, errorMsg);
            tableGroup.setErrorMessage(errorMsg);

            // 将错误信息保存到与 mapping 关联的 Meta
            String metaId = mapping.getMetaId();
            if (metaId != null) {
                try {
                    Meta meta = (Meta) profileComponent.getMeta(metaId);
                    if (meta != null) {
                        meta.setErrorMessage(errorMsg);
                        meta.setState(MetaEnum.ERROR.getCode());
                        profileComponent.editConfigModel(meta);
                    }
                } catch (Exception ex) {
                    logService.log(mapping, LogType.INIT, LogLevel.ERROR,
                            String.format("[保存错误到 Meta] 失败：metaId=%s, error=%s", metaId, ex.getMessage()));
                }
            }

            return;
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
        Connector sourceConnector = profileComponent.getConnector(mapping.getSourceConnectorId());
        Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());
        ConnectorConfig sConnConfig = sourceConnector.getConfig();
        ConnectorConfig tConnConfig = targetConnector.getConfig();

        // 关键修复：fieldMapping 不能为空
        if (CollectionUtils.isEmpty(tableGroup.getFieldMapping())) {
            // 不能抛异常，否则界面上的任务无法进入。
            logService.log(mapping, LogType.INIT, LogLevel.ERROR, "[字段映射]不能为空");
            return;
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

        profileComponent.addConfigModel(newTableGroup);
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
            if (tableGroup.getSourceTable() != null) {
                String connectorId = mapping.getSourceConnectorId();
                String tableName = tableGroup.getSourceTable().getName();

                // 修复 sourceTable.primaryKeys 缺失（ADR-0017 前旧数据没有该字段）
                if (CollectionUtils.isEmpty(tableGroup.getSourceTable().getPrimaryKeys())) {
                    Connector connector = profileComponent.getConnector(connectorId);
                    MetaInfo freshMeta = connectorFactory.getMetaInfo(connectorFactory.connect(connector.getConfig()),
                            tableName);
                    if (freshMeta == null) {
                        logService.log(mapping, LogType.CONFIG, LogLevel.ERROR,
                                String.format("[版本迁移]源表不存在，停止迁移: tableGroup=%s, sourceTable=%s",
                                        tableGroup.getId(), tableName));
                        return;
                    }
                    tableGroup.setSourceTable(freshMeta);
                }
            }
            if (tableGroup.getTargetTable() == null || StringUtils.isEmpty(tableGroup.getTargetTable().getName())) {
                MetaInfo table = tableGroup.getSourceTable().clone();
                tableGroup.setTargetTable(table);
            } else {
                String connectorId = mapping.getTargetConnectorId();
                String tableName = tableGroup.getTargetTable().getName();
                Connector connector = profileComponent.getConnector(connectorId);
                MetaInfo freshMeta = connectorFactory.getMetaInfo(connectorFactory.connect(connector.getConfig()),
                        tableName);
                tableGroup.setTargetTable(freshMeta);
            }
        }

        tableGroup.currentVersion = TableGroup.Version;
        profileComponent.editConfigModel(tableGroup);
    }
}
