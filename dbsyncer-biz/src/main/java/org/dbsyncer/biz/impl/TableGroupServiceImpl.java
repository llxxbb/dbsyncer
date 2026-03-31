/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.PrimaryKeyRequiredException;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.checker.impl.tablegroup.TableGroupChecker;
import org.dbsyncer.biz.task.TableGroupCountTask;
import org.dbsyncer.biz.util.FieldComparisonUtil;
import org.dbsyncer.biz.vo.FieldDiffFixVO;
import org.dbsyncer.biz.vo.FieldDiffFixItem;
import org.dbsyncer.biz.vo.FieldDiffItem;
import org.dbsyncer.biz.vo.FieldDifferenceVO;
import org.dbsyncer.common.dispatch.DispatchTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.manager.ManagerFactory;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.*;
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
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/11/27 23:14
 */
@Service
public class TableGroupServiceImpl extends BaseServiceImpl implements TableGroupService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

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

        // 【新增】保存原主键信息（在 checkEditConfigModel 修改之前）
        List<String> oldPrimaryKeys = PrimaryKeyUtil.findTablePrimaryKeys(tableGroup.getTargetTable());

        // 解析新主键参数
        String targetTablePK = params.get("targetTablePK");
        List<String> newPrimaryKeys = new ArrayList<>();
        if (StringUtil.isNotBlank(targetTablePK)) {
            String[] pks = StringUtil.split(targetTablePK, StringUtil.COMMA);
            for (String pk : pks) {
                newPrimaryKeys.add(pk.trim());
            }
        }

        // 应用主键参数（内存修改）
        TableGroup model = (TableGroup) tableGroupChecker.checkEditConfigModel(params);

        // 【修正】先执行 DDL，成功后再保存配置
        if (!oldPrimaryKeys.equals(newPrimaryKeys)) {
            Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());
            if (targetConnector != null) {
                try {
                    alterPrimaryKey(model, targetConnector, oldPrimaryKeys, newPrimaryKeys);
                } catch (Exception e) {
                    throw new RuntimeException("请确认表已经创建！修改主键约束失败：" + e.getMessage(), e);
                }
            }
        }

        // DDL 成功后保存配置
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
        
        // 合并驱动公共字段（更新 mapping.targetColumn）
        mergeMappingColumn(mapping);
        
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
                org.dbsyncer.common.model.Result result = connectorFactory.writerDDL(
                        connectorFactory.connect(targetConnector.getConfig()),
                        ddlConfig,
                        null);

                // 检查执行结果
                if (result != null && result.error != null) {
                    throw new Exception("请确认表已经创建！执行自定义字段 DDL 失败 ：" + result.error);
                }

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

    /**
     * 修改目标表的主键约束
     * 使用项目的 DDL 处理框架，由各数据库连接器提供各自的 SQL 实现
     *
     * @param tableGroup      表组对象
     * @param targetConnector 目标连接器
     * @param oldPrimaryKeys  原主键列表
     * @param newPrimaryKeys  新主键列表
     * @throws Exception 执行异常
     */
    public void alterPrimaryKey(TableGroup tableGroup, Connector targetConnector,
                                List<String> oldPrimaryKeys, List<String> newPrimaryKeys) throws Exception {
        if (CollectionUtils.isEmpty(newPrimaryKeys)) {
            throw new RuntimeException("新主键列表不能为空");
        }

        // 如果主键没有变化，跳过
        if (oldPrimaryKeys.equals(newPrimaryKeys)) {
            logger.info("主键未变化，跳过 DDL 执行");
            return;
        }

        // 使用 SqlTemplate 构建 DDL（由各数据库连接器提供实现）
        AbstractDatabaseConnector dbConnector =
                (AbstractDatabaseConnector) connectorFactory.getConnectorService(targetConnector.getConfig().getConnectorType());
        SqlTemplate sqlTemplate = dbConnector.getSqlTemplate();

        String tableName = tableGroup.getTargetTable().getName();
        String schema = ((org.dbsyncer.sdk.config.DatabaseConfig) targetConnector.getConfig()).getSchema();

        // 使用 SqlTemplate 构建 DDL
        String alterPkSql = sqlTemplate.buildAlterPrimaryKeySql(tableName, oldPrimaryKeys, newPrimaryKeys, schema);

        // 使用项目的 DDL 框架执行
        DDLConfig ddlConfig = new DDLConfig();
        ddlConfig.setSql("/*dbs*/" + alterPkSql);  // 添加前缀防止双向同步循环

        org.dbsyncer.common.model.Result result = connectorFactory.writerDDL(
                connectorFactory.connect(targetConnector.getConfig()),
                ddlConfig,
                null);

        if (StringUtil.isNotBlank(result.error)) {
            throw new RuntimeException("执行 DDL 失败：" + result.error);
        }

        logger.info("修改主键约束成功：{} -> {}", oldPrimaryKeys, newPrimaryKeys);
    }

    @Override
    public String resetTableGroups(String mappingId, String tableGroupIds, boolean truncateTarget) throws Exception {
        Assert.hasText(mappingId, "驱动ID不能为空");
        Assert.hasText(tableGroupIds, "表映射关系ID不能为空");

        Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.notNull(mapping, "驱动不存在");

        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        Assert.notNull(meta, "任务元信息不存在");

        meta.assertNotRunning();

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
            // 传入需要重置的 TableGroup ID 集合（用于日志记录）
            // partialClear 会统计所有 TableGroup，因为被重置的已经清零，所以实际上保留了未被重置的统计数据
            Set<String> resetTableGroupIds = tableGroupsToReset.stream()
                    .map(TableGroup::getId)
                    .collect(java.util.stream.Collectors.toSet());
            meta.partialClear(resetTableGroupIds);

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
     * @param mapping     驱动映射关系
     * @param tableGroups 表映射关系列表
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
                    String truncateSql = dbConnector.getSqlTemplate().buildTruncateTableSql(schema, tableName);
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

    @Override
    public FieldDifferenceVO getFieldDifference(String id) throws Exception {
        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "TableGroup不能为空");

        List<Field> sourceFields = tableGroup.getSourceTable().getColumn();
        List<Field> targetFields = tableGroup.getTargetTable().getColumn();

        FieldComparisonUtil.FieldComparisonResult comparison = FieldComparisonUtil.compareFields(sourceFields, targetFields);

        FieldDifferenceVO result = new FieldDifferenceVO();
        result.setAddedFields(comparison.getAddedFields());
        result.setMissingFields(comparison.getMissingFields());
        result.setTypeMismatched(comparison.getTypeMismatched());
        result.setLengthMismatched(comparison.getLengthMismatched());

        return result;
    }

    @Override
    public FieldDiffFixVO getFieldDiffFixPreview(String id) throws Exception {
        Assert.hasText(id, "TableGroup ID 不能为空");

        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "TableGroup 不存在");

        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        Assert.notNull(mapping, "Mapping 不存在");

        mapping.assertDisableEdit();

        FieldDifferenceVO diffVO = getFieldDifference(id);

        FieldDiffFixVO fixVO = new FieldDiffFixVO();
        fixVO.setTableGroupId(id);
        fixVO.setSourceTableName(tableGroup.getSourceTable().getName());
        fixVO.setTargetTableName(tableGroup.getTargetTable().getName());

        if (!diffVO.isHasDifference()) {
            fixVO.setHasSql(false);
            fixVO.setWarning("没有字段差异需要修复");
            return fixVO;
        }

        Connector sourceConnector = profileComponent.getConnector(mapping.getSourceConnectorId());
        Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());

        ConnectorService sourceConnectorService = connectorFactory.getConnectorService(sourceConnector.getConfig().getConnectorType());
        ConnectorService targetConnectorService = connectorFactory.getConnectorService(targetConnector.getConfig().getConnectorType());

        if (!isDatabaseConnector(sourceConnectorService) || !isDatabaseConnector(targetConnectorService)) {
            fixVO.setHasSql(false);
            fixVO.setWarning("只支持数据库类型的连接器");
            return fixVO;
        }

        AbstractDatabaseConnector sourceDbConnector = (AbstractDatabaseConnector) sourceConnectorService;
        AbstractDatabaseConnector targetDbConnector = (AbstractDatabaseConnector) targetConnectorService;

        List<FieldDiffFixItem> items = new ArrayList<>();
        List<String> sqlStatements = new ArrayList<>();
        boolean hasDropOperation = false;

        Map<String, Field> sourceFieldMap = FieldComparisonUtil.buildFieldMap(tableGroup.getSourceTable().getColumn());
        Map<String, Field> targetFieldMap = FieldComparisonUtil.buildFieldMap(tableGroup.getTargetTable().getColumn());

        hasDropOperation = processTargetAddedFields(diffVO.getAddedFields(), targetFieldMap, tableGroup, targetDbConnector, items, sqlStatements);
        processTargetMissingFields(diffVO.getMissingFields(), sourceFieldMap, tableGroup, sourceDbConnector, targetDbConnector, items, sqlStatements);
        processTargetTypeMismatched(diffVO.getTypeMismatched(), sourceFieldMap, tableGroup, sourceDbConnector, targetDbConnector, items, sqlStatements);
        processTargetLengthMismatched(diffVO.getLengthMismatched(), sourceFieldMap, tableGroup, sourceDbConnector, targetDbConnector, items, sqlStatements);

        fixVO.setItems(items);
        fixVO.setSqlStatements(sqlStatements);
        fixVO.setHasSql(!sqlStatements.isEmpty());

        if (hasDropOperation) {
            fixVO.setWarning("警告：包含 DROP COLUMN 操作，删除列将导致数据丢失，请谨慎操作！");
        } else if (!sqlStatements.isEmpty()) {
            fixVO.setWarning("注意：DDL 操作不可逆，请确认 SQL 语句后再执行");
        }

        return fixVO;
    }

    @Override
    public String executeFieldDiffFix(String id, List<String> selectedIds) throws Exception {
        Assert.hasText(id, "TableGroup ID 不能为空");

        TableGroup tableGroup = profileComponent.getTableGroup(id);
        Assert.notNull(tableGroup, "TableGroup 不存在");

        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        Assert.notNull(mapping, "Mapping 不存在");

        mapping.assertDisableEdit();

        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        if (meta != null) {
            meta.assertNotRunning();
        }

        synchronized (getLock(id)) {
            FieldDiffFixVO fixVO = getFieldDiffFixPreview(id);

            if (!fixVO.isHasSql()) {
                return "没有需要执行的 DDL 语句";
            }

            List<FieldDiffFixItem> itemsToFix;
            if (CollectionUtils.isEmpty(selectedIds)) {
                itemsToFix = fixVO.getItems();
            } else {
                itemsToFix = new ArrayList<>();
                for (FieldDiffFixItem item : fixVO.getItems()) {
                    if (selectedIds.contains(item.getId())) {
                        itemsToFix.add(item);
                    }
                }
            }

            if (CollectionUtils.isEmpty(itemsToFix)) {
                return "没有选中的修复项需要执行";
            }

            List<String> sqlStatements = new ArrayList<>();
            for (FieldDiffFixItem item : itemsToFix) {
                if (StringUtil.isNotBlank(item.getSql())) {
                    sqlStatements.add(item.getSql());
                }
            }

            if (CollectionUtils.isEmpty(sqlStatements)) {
                return "没有需要执行的 DDL 语句";
            }

            Connector connector = profileComponent.getConnector(mapping.getTargetConnectorId());
            String tableName = tableGroup.getTargetTable().getName();

            String connectorType = connector.getConfig().getConnectorType();
            ConnectorService connectorService = connectorFactory.getConnectorService(connectorType);

            if (!isDatabaseConnector(connectorService)) {
                throw new UnsupportedOperationException("只支持数据库类型的连接器");
            }

            validateIdentifier(tableName, "表名");

            ConnectorInstance connectorInstance = null;
            int successCount = 0;
            List<String> failedSqls = new ArrayList<>();

            try {
                connectorInstance = connectorFactory.connect(connector.getConfig());

                for (String sql : sqlStatements) {
                    try {
                        DDLConfig ddlConfig = new DDLConfig();
                        ddlConfig.setSql(sql);

                        org.dbsyncer.common.model.Result result = connectorFactory.writerDDL(connectorInstance, ddlConfig, null);

                        if (result.error != null && !result.error.isEmpty()) {
                            logger.error("DDL 执行失败 [tableGroupId={}]: {}, 错误: {}", id, sql, result.error);
                            failedSqls.add(sql + " (错误: " + result.error + ")");
                        } else {
                            logger.info("DDL 执行成功 [tableGroupId={}]: {}", id, sql);
                            successCount++;
                        }
                    } catch (Exception e) {
                        logger.error("DDL 执行异常 [tableGroupId={}]: {}", id, sql, e);
                        failedSqls.add(sql + " (异常: " + e.getMessage() + ")");
                    }
                }

                if (successCount > 0) {
                    try {
                        tableGroupChecker.refreshTableFields(tableGroup);
                        profileComponent.editTableGroup(tableGroup);
                        log(LogType.TableGroupLog.UPDATE, tableGroup);
                    } catch (Exception e) {
                        logger.error("刷新表字段失败", e);
                    }
                }
            } finally {
                if (connectorInstance != null) {
                    try {
                        connectorFactory.disconnect(connector.getConfig());
                        logger.debug("数据库连接已关闭 [tableGroupId={}]", id);
                    } catch (Exception e) {
                        logger.warn("关闭数据库连接失败 [tableGroupId={}]: {}", id, e.getMessage());
                    }
                }
            }

            int totalSelected = itemsToFix.size();
            if (failedSqls.isEmpty()) {
                String message = String.format("成功修复目标表字段差异，共执行 %d/%d 条 DDL 语句", successCount, totalSelected);
                logService.log(LogType.SystemLog.INFO, message);
                return message;
            } else {
                String message = String.format("部分 DDL 执行失败，成功 %d/%d 条，失败 %d 条。失败的 SQL: %s",
                        successCount, totalSelected, failedSqls.size(), String.join("; ", failedSqls));
                logService.log(LogType.SystemLog.ERROR, message);
                return message;
            }
        }
    }

    private boolean isDatabaseConnector(ConnectorService connectorService) {
        return connectorService instanceof AbstractDatabaseConnector;
    }

    // ==========================================
    // TARGET方向处理方法（以源表为基准修复目标表）
    // ==========================================

    /**
     * 【TARGET-1】处理目标表多出字段 -> 执行 DROP COLUMN
     * @return 是否包含DROP操作
     */
    private boolean processTargetAddedFields(List<FieldDiffItem> addedFields, Map<String, Field> targetFieldMap,
                                          TableGroup tableGroup, AbstractDatabaseConnector targetDbConnector,
                                          List<FieldDiffFixItem> items, List<String> sqlStatements) {
        if (CollectionUtils.isEmpty(addedFields)) {
            return false;
        }
        String tableName = tableGroup.getTargetTable().getName();
        final boolean[] hasDrop = {false};
        addedFields.forEach(diffItem -> {
            Field targetField = targetFieldMap.get(diffItem.getFieldName().toLowerCase());
            if (targetField == null) {
                return;
            }
            FieldDiffFixItem fixItem = createFixItem(diffItem, "ADDED", "DROP",
                    "目标表多出的字段，将删除", diffItem.getTargetType(), diffItem.getTargetLength(), null, null);
            String sql = targetDbConnector.getSqlTemplate().buildDropColumnSql(tableName, diffItem.getFieldName());
            fixItem.setSql(sql);
            items.add(fixItem);
            sqlStatements.add(sql);
            hasDrop[0] = true;
        });
        return hasDrop[0];
    }

    /**
     * 【TARGET-2】处理目标表缺少字段 -> 执行 ADD COLUMN
     */
    private void processTargetMissingFields(List<FieldDiffItem> missingFields, Map<String, Field> sourceFieldMap,
                                            TableGroup tableGroup, AbstractDatabaseConnector sourceDbConnector,
                                            AbstractDatabaseConnector targetDbConnector,
                                            List<FieldDiffFixItem> items, List<String> sqlStatements) {
        if (CollectionUtils.isEmpty(missingFields)) {
            return;
        }
        String tableName = tableGroup.getTargetTable().getName();
        missingFields.forEach(diffItem -> {
            Field sourceField = sourceFieldMap.get(diffItem.getFieldName().toLowerCase());
            if (sourceField == null) {
                return;
            }
            FieldDiffFixItem fixItem = createFixItem(diffItem, "MISSING", "ADD",
                    "目标表缺少的字段，将添加", null, null, diffItem.getSourceType(), diffItem.getSourceLength());
            Field fieldToAdd = convertFieldForTarget(sourceField, sourceDbConnector, targetDbConnector);
            String sql = targetDbConnector.getSqlTemplate().buildAddColumnSql(tableName, fieldToAdd);
            fixItem.setSql(sql);
            items.add(fixItem);
            sqlStatements.add(sql);
        });
    }

    /**
     * 【TARGET-3】处理类型不匹配字段 -> 执行 MODIFY COLUMN
     */
    private void processTargetTypeMismatched(List<FieldDiffItem> typeMismatched, Map<String, Field> sourceFieldMap,
                                             TableGroup tableGroup, AbstractDatabaseConnector sourceDbConnector,
                                             AbstractDatabaseConnector targetDbConnector,
                                             List<FieldDiffFixItem> items, List<String> sqlStatements) {
        if (CollectionUtils.isEmpty(typeMismatched)) {
            return;
        }
        String tableName = tableGroup.getTargetTable().getName();
        typeMismatched.forEach(diffItem -> {
            Field sourceField = sourceFieldMap.get(diffItem.getFieldName().toLowerCase());
            if (sourceField == null) {
                return;
            }
            FieldDiffFixItem fixItem = createFixItem(diffItem, "TYPE_MISMATCH", "MODIFY",
                    "类型不匹配，将修改为目标类型", diffItem.getTargetType(), null, diffItem.getSourceType(), null);
            Field fieldToModify = convertFieldForTarget(sourceField, sourceDbConnector, targetDbConnector);
            String sql = targetDbConnector.getSqlTemplate().buildModifyColumnSql(tableName, fieldToModify);
            fixItem.setSql(sql);
            items.add(fixItem);
            sqlStatements.add(sql);
        });
    }

    /**
     * 【TARGET-4】处理长度不匹配字段 -> 执行 MODIFY COLUMN
     */
    private void processTargetLengthMismatched(List<FieldDiffItem> lengthMismatched, Map<String, Field> sourceFieldMap,
                                               TableGroup tableGroup, AbstractDatabaseConnector sourceDbConnector,
                                               AbstractDatabaseConnector targetDbConnector,
                                               List<FieldDiffFixItem> items, List<String> sqlStatements) {
        if (CollectionUtils.isEmpty(lengthMismatched)) {
            return;
        }
        String tableName = tableGroup.getTargetTable().getName();
        lengthMismatched.forEach(diffItem -> {
            Field sourceField = sourceFieldMap.get(diffItem.getFieldName().toLowerCase());
            if (sourceField == null) {
                return;
            }
            String description = String.format("源长度(%s) ≠ 目标长度(%s)，将修改为目标长度",
                    diffItem.getSourceLength(), diffItem.getTargetLength());
            FieldDiffFixItem fixItem = createFixItem(diffItem, "LENGTH_MISMATCH", "MODIFY", description,
                    diffItem.getTargetType(), diffItem.getTargetLength(), diffItem.getSourceType(), diffItem.getSourceLength());
            Field fieldToModify = convertFieldForTarget(sourceField, sourceDbConnector, targetDbConnector);
            String sql = targetDbConnector.getSqlTemplate().buildModifyColumnSql(tableName, fieldToModify);
            fixItem.setSql(sql);
            items.add(fixItem);
            sqlStatements.add(sql);
        });
    }

    /**
     * 创建FieldDiffFixItem的工厂方法
     */
    private FieldDiffFixItem createFixItem(FieldDiffItem diffItem, String diffType, String operation,
                                           String description, String targetType, Long targetLength,
                                           String sourceType, Long sourceLength) {
        FieldDiffFixItem fixItem = new FieldDiffFixItem();
        fixItem.setFieldName(diffItem.getFieldName());
        fixItem.setDiffType(diffType);
        fixItem.setOperation(operation);
        fixItem.setDescription(description);
        fixItem.setId(diffItem.getFieldName() + "_" + diffType);
        fixItem.setTargetType(targetType);
        fixItem.setTargetLength(targetLength);
        fixItem.setSourceType(sourceType);
        fixItem.setSourceLength(sourceLength);
        return fixItem;
    }

    private Field convertFieldForTarget(Field sourceField, AbstractDatabaseConnector sourceDbConnector,
                                         AbstractDatabaseConnector targetDbConnector) {
        org.dbsyncer.sdk.schema.SchemaResolver sourceSchemaResolver = sourceDbConnector.getSchemaResolver();
        org.dbsyncer.sdk.schema.SchemaResolver targetSchemaResolver = targetDbConnector.getSchemaResolver();

        if (sourceSchemaResolver != null && targetSchemaResolver != null) {
            Field standardField = sourceSchemaResolver.toStandardType(sourceField);
            Field targetField = targetSchemaResolver.fromStandardType(standardField);
            targetField.setPk(sourceField.isPk());
            return targetField;
        }
        return sourceField;
    }

    private void validateIdentifier(String identifier, String name) {
        if (StringUtil.isBlank(identifier)) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(name + " 包含非法字符，只能包含字母、数字、下划线，且必须以字母或下划线开头: " + identifier);
        }
        if (identifier.length() > 64) {
            throw new IllegalArgumentException(name + " 长度不能超过 64 个字符: " + identifier);
        }
    }

    private Object getLock(String id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

}
