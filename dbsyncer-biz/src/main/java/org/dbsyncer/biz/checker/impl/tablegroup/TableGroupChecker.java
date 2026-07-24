/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.biz.checker.impl.tablegroup;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.RepeatedTableGroupException;
import org.dbsyncer.biz.checker.AbstractChecker;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.service.TableGroupModelService;
import org.dbsyncer.sdk.model.ConfigModel;
import org.dbsyncer.sdk.model.FieldMapping;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;

import java.util.*;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2020-01-08 15:17
 */
@Component
public class TableGroupChecker extends AbstractChecker {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private TableGroupModelService tableGroupModelService;

    @Override
    public ConfigModel checkAddConfigModel(Map<String, String> params) throws Exception {
        logger.info("params:{}", params);
        String mappingId = params.get("mappingId");
        String sourceTable = params.get("sourceTable");
        String targetTable = params.get("targetTable");
        String sourceTablePK = params.get("sourceTablePK");
        String targetTablePK = params.get("targetTablePK");
        Assert.hasText(mappingId, "tableGroup mappingId is empty.");
        Assert.hasText(sourceTable, "tableGroup sourceTable is empty.");
        Assert.hasText(targetTable, "tableGroup targetTable is empty.");
        Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.notNull(mapping, "mapping can not be null.");

        // 检查是否存在重复映射关系（新增时排除 ID 为 null）
        checkRepeatedTable(mappingId, sourceTable, targetTable, null);

        // 获取连接器信息
        TableGroup tableGroup = tableGroupModelService.create(mappingId, sourceTable);
        
        // 统一初始化源表和目标表元数据
        initTableGroupMeta(tableGroup, mapping, sourceTable, sourceTablePK, targetTable, targetTablePK);

        this.modifyConfigModel(tableGroup, params);

        // 验证转换器规则：每个字段必须有且仅有一个根转换器
        ConvertUtil.validateFieldConverterRule(tableGroup.getConvert());

        return tableGroup;
    }

    @Override
    public ConfigModel checkEditConfigModel(Map<String, String> params) throws Exception {
        logger.debug("params:{}", params);
        Assert.notEmpty(params, "TableGroupChecker check params is null.");
        String id = params.get(ConfigConstant.CONFIG_MODEL_ID);
        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "Can not find tableGroup.");
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        Assert.notNull(mapping, "mapping can not be null.");

        // 处理目标表变化（前端已感应并提交，后端直接使用）
        String newTargetTableName = params.get("targetTable");
        String newTargetTablePK = params.get("targetTablePK");
        Assert.notNull(newTargetTableName, "targetTable can not be null.");
        Assert.notNull(newTargetTablePK, "targetTablePK can not be null.");


        // 应用目标表变化（仅更新 MetaInfo，主键配置由 applyPrimaryKeyConfig 统一处理）
        checkRepeatedTable(mapping.getId(), tableGroup.getSourceTable().getName(), newTargetTableName, id);
        tableGroup.setTargetTable(getMetaInfo(mapping.getTargetConnectorId(), newTargetTableName, newTargetTablePK));

        // 修改基本配置
        this.modifyConfigModel(tableGroup, params);
        this.modifySuperConfigModel(tableGroup, params);

        // 验证转换器规则：每个字段必须有且仅有一个根转换器
        ConvertUtil.validateFieldConverterRule(tableGroup.getConvert());

        return tableGroup;
    }

    /**
     * 应用字段映射关系（JSON 格式：[{"source":"a","target":"b"}]）
     * 无映射参数时自动从源表构建
     *
     * @param tableGroup   表组
     * @param fieldMapping JSON 格式字段映射
     */
    public void applyFieldMapping(TableGroup tableGroup, String fieldMapping) {
        if (StringUtil.isNotBlank(fieldMapping)) {
            setFieldMapping(tableGroup, fieldMapping);
        } else {
            buildFieldMappingFromSourceTable(tableGroup);
        }
    }

    /**
     * 初始化表组元数据（源表和目标表）
     * 
     * @param tableGroup      表组
     * @param mapping         映射配置
     * @param sourceTable     源表名称（刷新时为 null，使用 tableGroup 已有值）
     * @param sourceTablePK   源表主键（刷新时为 null，使用数据库默认顺序）
     * @param targetTable     目标表名称（刷新时为 null，使用 tableGroup 已有值）
     * @param targetTablePK   目标表主键（刷新时为 null，使用已有配置）
     * @return 表组（已设置源表和目标表）
     */
    public TableGroup initTableGroupMeta(TableGroup tableGroup, Mapping mapping, String sourceTable, String sourceTablePK, String targetTable, String targetTablePK) throws Exception {
        // 刷新场景：使用 tableGroup 现有的表名
        String sourceTableName = sourceTable != null ? sourceTable : tableGroup.getSourceTable().getName();
        String targetTableName = targetTable != null ? targetTable : tableGroup.getTargetTable().getName();
        targetTableName = targetTableName != null ? targetTableName : sourceTableName; // 目标表名为空时使用源表名
        
        // 初始化源表
        tableGroup.setSourceTable(getMetaInfo(mapping.getSourceConnectorId(), sourceTableName, sourceTablePK));
        
        // 初始化目标表
        MetaInfo targetMetaInfo = getMetaInfo(mapping.getTargetConnectorId(), targetTableName, targetTablePK);
        
        // 新增场景：如果目标表不存在，深度复制源表信息（仅表名不同）
        assert tableGroup.getSourceTable() != null;
        if (targetMetaInfo == null && tableGroup.getSourceTable() != null) {
            targetMetaInfo = tableGroup.getSourceTable().clone();
            targetMetaInfo.setName(targetTableName);
        }
        tableGroup.setTargetTable(targetMetaInfo);
        
        return tableGroup;
    }

    private MetaInfo getMetaInfo(String connectorId, String tableName, String primaryKeyStr) throws Exception {
        Connector connector = profileComponent.getConnector(connectorId);
        MetaInfo metaInfo = connectorFactory.getMetaInfo(connectorFactory.connect(connector.getConfig()), tableName);
        if (metaInfo == null) {
            return null;
        }
        
        // 自定义主键：使用传入的主键配置（覆盖数据库默认顺序）
        if (StringUtil.isNotBlank(primaryKeyStr)) {
            List<String> primaryKeys = Arrays.asList(primaryKeyStr.split(","));
            metaInfo.setPrimaryKeys(primaryKeys);
        }
        
        return metaInfo;
    }

    /**
     * 检查是否存在重复映射关系
     *
     * @param mappingId           映射 ID
     * @param sourceTable         源表名称
     * @param targetTable         目标表名称
     * @param excludeTableGroupId 排除的表映射 ID（编辑时使用，新增时传 null）
     */
    private void checkRepeatedTable(String mappingId, String sourceTable, String targetTable, String excludeTableGroupId) throws Exception {
        List<TableGroup> list = profileComponent.getTableGroupAll(mappingId);
        if (!CollectionUtils.isEmpty(list)) {
            for (TableGroup g : list) {
                // 排除自身（编辑时）
                if (StringUtil.isNotBlank(excludeTableGroupId) && StringUtil.equals(excludeTableGroupId, g.getId())) {
                    continue;
                }
                // 数据源表和目标表都存在
                if (StringUtil.equalsIgnoreCase(sourceTable, g.getSourceTable().getName()) && StringUtil.equalsIgnoreCase(targetTable, g.getTargetTable().getName())) {
                    final String error = String.format("映射关系已存在.%s > %s", sourceTable, targetTable);
                    logger.error(error);
                    throw new RepeatedTableGroupException(error);
                }
            }
        }
    }

    /**
     * 解析映射关系
     *
     * @param tableGroup
     * @param json       [{"source":"id","target":"id"}]
     * @return
     */
    public void setFieldMapping(TableGroup tableGroup, String json) {
        List<Map<String, Object>> mappings = JsonUtil.parseList(json);
        if (null == mappings) {
            throw new BizException("映射关系不能为空");
        }

        List<FieldMapping> list = new ArrayList<>();
        Set<String> addedTargetNames = new HashSet<>();
        Map<String, Object> row = null;
        Field s = null;
        Field t = null;
        for (int i = 0, len = mappings.size(); i < len; i++) {
            row = mappings.get(i);
            String sourceName = row.get("source") != null ? row.get("source").toString() : null;
            String targetName = row.get("target") != null ? row.get("target").toString() : null;
            s = tableGroup.getSourceTable().findColumnByName(sourceName);
            t = tableGroup.getTargetTable().findColumnByName(targetName);
            if (null == s && null == t) {
                continue;
            }
            // 用源字段信息作为目标字段信息，但需要进行类型标准化和转换
            if (null == t) {
                // 获取源连接器和目标连接器的 SchemaResolver
                Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
                ConnectorConfig sourceConnectorConfig = getConnectorConfig(mapping.getSourceConnectorId());
                ConnectorConfig targetConnectorConfig = getConnectorConfig(mapping.getTargetConnectorId());
                ConnectorService<?, ?> sourceConnectorService = connectorFactory.getConnectorService(sourceConnectorConfig.getConnectorType());
                ConnectorService<?, ?> targetConnectorService = connectorFactory.getConnectorService(targetConnectorConfig.getConnectorType());
                SchemaResolver sourceSchemaResolver = sourceConnectorService.getSchemaResolver();
                SchemaResolver targetSchemaResolver = targetConnectorService.getSchemaResolver();

                // 1. 先用源连接器将源字段标准化为 Java 标准类型
                Field standardField = sourceSchemaResolver.toStandardType(s);

                // 2. 再用目标连接器将 Java 标准类型转换为目标数据库类型
                if (null == targetSchemaResolver) // 如下游是 kafka
                    t = standardField;
                else
                    t = targetSchemaResolver.fromStandardType(standardField);
            }

            if (null != t) {
                t.setPk((Boolean) row.get("pk"));
            }
            // 【修复】去重：使用目标字段名（不区分大小写）作为 key，避免重复添加
            if (StringUtil.isNotBlank(targetName)) {
                String key = targetName.toLowerCase();
                if (addedTargetNames.contains(key)) {
                    // 重复的目标字段，跳过
                    continue;
                }
                addedTargetNames.add(key);
            }
            list.add(new FieldMapping(s, t));
        }
        tableGroup.setFieldMapping(list);
    }

    /**
     * 基于源表字段构建 fieldMapping
     * 依据源表字段构建 target 字段（通过类型转换）
     * 目标表的主键字段必须来源于源表的主键字段
     *
     * @param tableGroup 表映射组
     */
    public void buildFieldMappingFromSourceTable(TableGroup tableGroup) {
        List<Field> sCol = tableGroup.getSourceTable().getColumn();

        if (CollectionUtils.isEmpty(sCol)) {
            tableGroup.setFieldMapping(new ArrayList<>());
            return;
        }

        // 基于源表所有字段构建 fieldMapping，依据源表字段构建 target 字段
        List<FieldMapping> fieldMappingList = new ArrayList<>();
        for (Field sourceField : sCol) {
            fieldMappingList.add(new FieldMapping(sourceField, sourceField));
        }

        tableGroup.setFieldMapping(fieldMappingList);
    }

    private ConnectorConfig getConnectorConfig(String connectorId) {
        return profileComponent.getConnector(connectorId).getConfig();
    }

}
