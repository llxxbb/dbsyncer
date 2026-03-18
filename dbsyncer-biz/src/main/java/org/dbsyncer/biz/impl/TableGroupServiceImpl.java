/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.PrimaryKeyRequiredException;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.checker.impl.tablegroup.TableGroupChecker;
import org.dbsyncer.biz.task.TableGroupCountTask;
import org.dbsyncer.common.dispatch.DispatchTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
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
                    logger.error("执行主键 DDL 失败，配置未保存", e);
                    throw new RuntimeException("修改主键约束失败：" + e.getMessage(), e);
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
        
        // 版本迁移：如果 currentVersion=1，自动构建 targetTablePK 并升级版本
        if (tableGroup.currentVersion == 1) {
            migrateVersion1ToVersion2(tableGroup);
            // 保存升级后的版本
            profileComponent.editTableGroup(tableGroup);
        }
        
        return tableGroup;
    }

    /**
     * 版本迁移：从 version 1 升级到 version 2
     * 自动构建 targetTablePK 字段
     */
    private void migrateVersion1ToVersion2(TableGroup tableGroup) {
        logger.info("TableGroup [{}] 从 version 1 升级到 version 2", tableGroup.getId());
        
        Table targetTable = tableGroup.getTargetTable();
        if (targetTable != null && targetTable.getColumn() != null) {
            // 从目标表字段中提取主键
            List<String> primaryKeys = targetTable.getColumn().stream()
                    .filter(Field::isPk)
                    .map(Field::getName)
                    .collect(Collectors.toList());
            
            if (!primaryKeys.isEmpty()) {
                String targetTablePK = String.join(",", primaryKeys);
                tableGroup.setTargetTablePK(targetTablePK);
                logger.info("自动构建 targetTablePK: {}", targetTablePK);
            }
        }
        
        // 升级版本号
        tableGroup.currentVersion = 2;
        logger.info("TableGroup [{}] 版本升级完成", tableGroup.getId());
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

    /**
     * 修改目标表的主键约束
     * 使用项目的 DDL 处理框架，由各数据库连接器提供各自的 SQL 实现
     *
     * @param tableGroup 表组对象
     * @param targetConnector 目标连接器
     * @param oldPrimaryKeys 原主键列表
     * @param newPrimaryKeys 新主键列表
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

}
