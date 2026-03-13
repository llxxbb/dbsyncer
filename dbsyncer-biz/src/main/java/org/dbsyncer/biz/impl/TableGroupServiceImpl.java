/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.PrimaryKeyRequiredException;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.checker.impl.tablegroup.TableGroupChecker;
import org.dbsyncer.biz.task.TableGroupCountTask;
import org.dbsyncer.common.dispatch.DispatchTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.manager.ManagerFactory;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.enums.MetaEnum;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/11/27 23:14
 */
@Service
public class TableGroupServiceImpl extends BaseServiceImpl implements TableGroupService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private LogService logService;

    @Resource
    private TableGroupChecker tableGroupChecker;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ParserComponent parserComponent;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private DispatchTaskService dispatchTaskService;

    @Resource
    private ManagerFactory managerFactory;

    @Override
    public String add(Map<String, String> params) throws Exception {
        String mappingId = params.get("mappingId");
        Mapping mapping = profileComponent.getMapping(mappingId);
        // 检查是否禁止编辑
        mapping.assertDisableEdit();

        synchronized (LOCK) {
            // table1, table2
            String[] sourceTableArray = StringUtil.split(params.get("sourceTable"), StringUtil.VERTICAL_LINE);
            String[] targetTableArray = StringUtil.split(params.get("targetTable"), StringUtil.VERTICAL_LINE);
            int tableSize = sourceTableArray.length;
            Assert.isTrue(tableSize == targetTableArray.length, "数据源表和目标源表关系必须为一组");

            String id = null;
            List<String> list = new ArrayList<>();
            for (int i = 0; i < tableSize; i++) {
                try {
                    params.put("sourceTable", sourceTableArray[i]);
                    params.put("targetTable", targetTableArray[i]);
                    TableGroup model = (TableGroup) tableGroupChecker.checkAddConfigModel(params);
                    log(LogType.TableGroupLog.INSERT, model);
                    int tableGroupCount = profileComponent.getTableGroupCount(mappingId);
                    model.setIndex(tableGroupCount + 1);
                    id = profileComponent.addTableGroup(model);
                    // 【新增】执行自定义字段 DDL
                    List<Convert> customConverts = model.getConvert();
                    if (customConverts != null && !customConverts.isEmpty()) {
                        List<Field> customFields = new ArrayList<>();
                        for (Convert convert : customConverts) {
                            Field fieldMetadata = convert.getFieldMetadata();
                            if (fieldMetadata != null) {
                                customFields.add(fieldMetadata);
                            }
                        }
                        if (!customFields.isEmpty()) {
                            try {
                                executeCustomFieldDDL(model, customFields);
                                refreshTableFieldsAfterDDL(model);
                                profileComponent.editTableGroup(model);
                            } catch (Exception e) {
                                // DDL 失败，回滚配置
                                profileComponent.removeTableGroup(id);
                                throw new RuntimeException("执行自定义字段 DDL 失败：" + e.getMessage(), e);
                            }
                        }
                    }
                    // 初始化 TableGroup（设置运行时组件并初始化 command）
                    model.isInit = false;
                    model.initTableGroup(parserComponent, profileComponent, connectorFactory);
                    list.add(id);
                } catch (PrimaryKeyRequiredException e) {
                    // 如果数据表没有主键，则跳过该表并记录日志
                    String sourceTableName = sourceTableArray[i];
                    String targetTableName = targetTableArray[i];
                    String mappingName = mapping != null ? mapping.getName() : mappingId;
                    String msg = String.format("跳过缺少主键的表组 - 驱动:%s, 源表:%s, 目标表:%s, 原因:%s",
                            mappingName, sourceTableName, targetTableName, e.getMessage());
                    logger.warn(msg);
                    logService.log(LogType.SystemLog.ERROR, msg);
                }
            }
            submitTableGroupCountTask(mapping, list);

            // 合并驱动公共字段
            mergeMappingColumn(mapping);
            return 1 < tableSize ? String.valueOf(tableSize) : id;
        }
    }

    @Override
    public String edit(Map<String, String> params) throws Exception {
        String id = params.get(ConfigConstant.CONFIG_MODEL_ID);
        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "Can not find tableGroup.");
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        // 检查是否禁止编辑
        mapping.assertDisableEdit();

        TableGroup model = (TableGroup) tableGroupChecker.checkEditConfigModel(params);
        log(LogType.TableGroupLog.UPDATE, model);
        profileComponent.editTableGroup(model);

        // 执行自定义字段 DDL
        List<Convert> customConverts = model.getConvert();
        if (customConverts != null && !customConverts.isEmpty()) {
            List<Field> customFields = new ArrayList<>();
            for (Convert convert : customConverts) {
                Field fieldMetadata = convert.getFieldMetadata();
                if (fieldMetadata != null) {
                    customFields.add(fieldMetadata);
                }
            }
            if (!customFields.isEmpty()) {
                try {
                    executeCustomFieldDDL(model, customFields);
                    refreshTableFieldsAfterDDL(model);
                    profileComponent.editTableGroup(model);
                } catch (Exception e) {
                    logger.error("执行自定义字段 DDL 失败，回滚配置", e);
                    throw new RuntimeException("执行自定义字段 DDL 失败：" + e.getMessage(), e);
                }
            }
        }
        // 初始化 TableGroup（设置运行时组件并初始化 command）
        model.isInit = false;
        model.initTableGroup(parserComponent, profileComponent, connectorFactory);
        List<String> list = new ArrayList<>();
        list.add(model.getId());
        submitTableGroupCountTask(mapping, list);
        return id;
    }

    @Override
    public String refreshFields(String id) throws Exception {
        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "Can not find tableGroup.");

        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        // 检查是否禁止编辑
        mapping.assertDisableEdit();

        tableGroupChecker.refreshTableFields(tableGroup);
        return profileComponent.editTableGroup(tableGroup);
    }

    @Override
    public boolean remove(String mappingId, String ids) throws Exception {
        Assert.hasText(mappingId, "Mapping id can not be null");
        Assert.hasText(ids, "TableGroup ids can not be null");
        Mapping mapping = profileComponent.getMapping(mappingId);
        // 检查是否禁止编辑
        mapping.assertDisableEdit();

        // 批量删除表
        Stream.of(StringUtil.split(ids, ",")).parallel().forEach(id -> {
            TableGroup model = null;
            try {
                model = profileComponent.getTableGroup(id);
                log(LogType.TableGroupLog.DELETE, model);
                profileComponent.removeTableGroup(id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 合并驱动公共字段
        mergeMappingColumn(mapping);
        submitTableGroupCountTask(mapping, Collections.emptyList());

        // 重置排序
        resetTableGroupAllIndex(mappingId);
        return true;
    }

    @Override
    public TableGroup getTableGroup(String id) throws Exception {
        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "TableGroup can not be null");
        return tableGroup;
    }

    @Override
    public List<TableGroup> getTableGroupAll(String mappingId) throws Exception {
        return profileComponent.getSortedTableGroupAll(mappingId);
    }

    private void resetTableGroupAllIndex(String mappingId) throws Exception {
        synchronized (LOCK) {
            List<TableGroup> list = profileComponent.getSortedTableGroupAll(mappingId);
            int size = list.size();
            int i = size;
            while (i > 0) {
                TableGroup g = list.get(size - i);
                g.setIndex(i);
                profileComponent.editConfigModel(g);
                i--;
            }
        }
    }

    private void mergeMappingColumn(Mapping mapping) throws Exception {
        List<TableGroup> groups = profileComponent.getTableGroupAll(mapping.getId());

        List<Field> sourceColumn = null;
        List<Field> targetColumn = null;
        for (TableGroup g : groups) {
            sourceColumn = pickCommonFields(sourceColumn, g.getSourceTable().getColumn());
            targetColumn = pickCommonFields(targetColumn, g.getTargetTable().getColumn());
        }

        mapping.setSourceColumn(sourceColumn);
        mapping.setTargetColumn(targetColumn);
        profileComponent.editConfigModel(mapping);
    }

    private List<Field> pickCommonFields(List<Field> column, List<Field> target) {
        if (CollectionUtils.isEmpty(column) || CollectionUtils.isEmpty(target)) {
            return target;
        }
        List<Field> list = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        column.forEach(f -> keys.add(f.getName()));
        target.forEach(f -> {
            if (keys.contains(f.getName())) {
                list.add(f);
            }
        });
        return list;
    }

    /**
     * 提交统计驱动表总数任务
     */
    private void submitTableGroupCountTask(Mapping mapping, List<String> list) {
        TableGroupCountTask task = new TableGroupCountTask();
        task.setMappingId(mapping.getId());
        task.setTableGroups(list);
        task.setParserComponent(parserComponent);
        task.setProfileComponent(profileComponent);
        task.setTableGroupService(this);
        dispatchTaskService.execute(task);
    }


    // ========== 自定义字段 DDL 相关方法 ==========

    /**
     * 提取自定义字段（有 fieldMetadata 的 convert）
     */
    private List<Field> extractCustomFields(TableGroup tableGroup) {
        List<Field> customFields = new ArrayList<>();

        for (Convert convert : tableGroup.getConvert()) {
            Field metadata = convert.getFieldMetadata();
            if (metadata != null) {
                customFields.add(metadata);
            }
        }

        return customFields;
    }

    /**
     * 执行自定义字段 DDL
     */
    private void executeCustomFieldDDL(TableGroup tableGroup, List<Field> customFields) throws Exception {
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        org.dbsyncer.parser.model.Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());

        SqlTemplate sqlTemplate = ((org.dbsyncer.sdk.connector.database.Database) connectorFactory.getConnectorService(targetConnector.getConfig())).getSqlTemplate();

        for (Field field : customFields) {
            try {
                // 生成 DDL
                String ddl = sqlTemplate.buildAddColumnSql(tableGroup.getTargetTable().getName(), field);

                // 执行 DDL
                DDLConfig ddlConfig = new DDLConfig();
                ddlConfig.setSql(ddl);

                // 使用 connectorFactory 执行 DDL
                connectorFactory.writerDDL(
                        connectorFactory.connect(targetConnector.getConfig()),
                        ddlConfig,
                        null);

                logger.info("自定义字段 DDL 执行成功：{}.{}",
                        tableGroup.getTargetTable().getName(), field.getName());
            } catch (Exception e) {
                logger.error("DDL 执行异常：{}", field.getName(), e);
                throw e;
            }
        }
    }

    /**
     * DDL 成功后刷新元数据
     */
    private void refreshTableFieldsAfterDDL(TableGroup tableGroup) throws Exception {
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());

        // 重新获取目标表元数据
        MetaInfo targetMetaInfo = parserComponent.getMetaInfo(
                mapping.getTargetConnectorId(),
                tableGroup.getTargetTable().getName());

        // 更新 TableGroup
        tableGroup.getTargetTable().setColumn(targetMetaInfo.getColumn());

        // 重新初始化 command
        tableGroup.initCommand(mapping, connectorFactory);
    }

    @Override
    public String resetTableGroups(String mappingId, String tableGroupIds, boolean truncateTarget) throws Exception {
        Assert.hasText(mappingId, "驱动ID不能为空");
        Assert.hasText(tableGroupIds, "表映射关系ID不能为空");

        Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.notNull(mapping, "驱动不存在");

        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        Assert.notNull(meta, "任务元信息不存在");

        if (meta.isRunning()) {
            throw new BizException("任务正在运行中，请先停止任务再执行重新同步操作");
        }

        synchronized (LOCK) {
            logger.info("重新同步表映射关系：驱动={}, 表组IDs={}", mapping.getName(), tableGroupIds);

            String[] ids = StringUtil.split(tableGroupIds, ",");
            List<TableGroup> tableGroupsToReset = new ArrayList<>();

            for (String id : ids) {
                TableGroup tableGroup = profileComponent.getTableGroup(id.trim());
                if (tableGroup != null && mappingId.equals(tableGroup.getMappingId())) {
                    tableGroupsToReset.add(tableGroup);
                }
            }

            if (tableGroupsToReset.isEmpty()) {
                throw new BizException("未找到有效的表映射关系");
            }

            if (truncateTarget) {
                truncateTargetTables(mapping, tableGroupsToReset);
            }

            for (TableGroup tableGroup : tableGroupsToReset) {
                tableGroup.clear();
                profileComponent.editConfigModel(tableGroup);
                log(LogType.TableGroupLog.UPDATE, tableGroup);
            }

            // 使用 partialClear 而不是 clear，保留 syncPhase 和 snapshot
            meta.partialClear();

            mapping.setUpdateTime(java.time.Instant.now().toEpochMilli());
            profileComponent.editConfigModel(mapping);

            String model = org.dbsyncer.sdk.enums.ModelEnum.getModelEnum(mapping.getModel()).getName();
            sendNotifyMessage("重新同步", String.format("手动重新同步驱动：%s(%s)，共 %d 个表映射关系", 
                    mapping.getName(), model, tableGroupsToReset.size()));

            // 传递要同步的 TableGroup 列表，实现选择性同步
            managerFactory.start(mapping, tableGroupsToReset);
            log(LogType.MappingLog.RUNNING, mapping);

            logger.info("重新同步完成，已自动启动任务：{}", mapping.getName());

            submitTableGroupCountTask(mapping, tableGroupsToReset.stream()
                    .map(TableGroup::getId)
                    .collect(java.util.stream.Collectors.toList()));

            return String.format("重新同步成功，共重置 %d 个表映射关系，任务已自动启动", tableGroupsToReset.size());
        }
    }

    /**
     * 使用 TRUNCATE 清空目标源表
     *
     * @param mapping       驱动映射关系
     * @param tableGroups   表映射关系列表
     */
    private void truncateTargetTables(Mapping mapping, List<TableGroup> tableGroups) {
        if (CollectionUtils.isEmpty(tableGroups)) {
            return;
        }

        Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());
        if (targetConnector == null) {
            logger.warn("目标连接器不存在，跳过 TRUNCATE 操作");
            return;
        }

        ConnectorService targetConnectorService = connectorFactory.getConnectorService(targetConnector.getConfig().getConnectorType());

        if (!(targetConnectorService instanceof AbstractDatabaseConnector)) {
            logger.info("目标连接器类型 {} 不支持 TRUNCATE 操作，跳过清空目标表", targetConnector.getConfig().getConnectorType());
            return;
        }

        AbstractDatabaseConnector dbConnector = (AbstractDatabaseConnector) targetConnectorService;

        try {
            ConnectorInstance connectorInstance = connectorFactory.connect(targetConnector.getConfig());
            DatabaseConnectorInstance dbInstance = (DatabaseConnectorInstance) connectorInstance;

            String schema = ((org.dbsyncer.sdk.config.DatabaseConfig) targetConnector.getConfig()).getSchema();

            for (TableGroup tableGroup : tableGroups) {
                Table targetTable = tableGroup.getTargetTable();
                if (targetTable == null || StringUtil.isBlank(targetTable.getName())) {
                    continue;
                }

                String tableName = targetTable.getName();
                try {
                    String truncateSql = buildTruncateSql(dbConnector.getSqlTemplate(), schema, tableName);
                    logger.info("执行 TRUNCATE 目标表: {}", truncateSql);

                    dbInstance.execute(databaseTemplate -> {
                        databaseTemplate.execute(truncateSql);
                        return null;
                    });

                    logger.info("TRUNCATE 目标表成功: {}", tableName);
                } catch (Exception e) {
                    logger.error("TRUNCATE 目标表失败: {}, 错误: {}", tableName, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("TRUNCATE 目标表操作失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建 TRUNCATE SQL 语句
     *
     * @param sqlTemplate SQL 模板
     * @param schema      架构名
     * @param tableName   表名
     * @return TRUNCATE SQL 语句
     */
    private String buildTruncateSql(SqlTemplate sqlTemplate, String schema, String tableName) {
        String quotedTableName = sqlTemplate.buildTable(schema, tableName);
        return "TRUNCATE TABLE " + quotedTableName;
    }

}
