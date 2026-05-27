package org.dbsyncer.parser.ddl.impl;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ddl.AlterStrategy;
import org.dbsyncer.parser.ddl.DDLParser;
import org.dbsyncer.parser.ddl.alter.AddStrategy;
import org.dbsyncer.parser.ddl.alter.ChangeStrategy;
import org.dbsyncer.parser.ddl.alter.DropStrategy;
import org.dbsyncer.parser.ddl.alter.ModifyStrategy;
import org.dbsyncer.parser.model.FieldMapping;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.database.Database;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.enums.DDLOperationEnum;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.parser.ddl.converter.IRToTargetConverter;
import org.dbsyncer.sdk.parser.ddl.converter.SourceToIRConverter;
import org.dbsyncer.sdk.parser.ddl.ir.DDLIntermediateRepresentation;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ddl解析器, 支持类型参考：{@link DDLOperationEnum}
 */
@Component
public class DDLParserImpl implements DDLParser {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Map<AlterOperation, AlterStrategy> STRATEGIES = new ConcurrentHashMap<>();

    @Resource
    private ConnectorFactory connectorFactory;

    @PostConstruct
    private void init() {
        STRATEGIES.putIfAbsent(AlterOperation.MODIFY, new ModifyStrategy());
        STRATEGIES.putIfAbsent(AlterOperation.ADD, new AddStrategy());
        STRATEGIES.putIfAbsent(AlterOperation.CHANGE, new ChangeStrategy());
        STRATEGIES.putIfAbsent(AlterOperation.DROP, new DropStrategy());
    }

    @Override
    public DDLConfig parse(ConnectorService connectorService, TableGroup tableGroup, String sql)
            throws JSQLParserException {
        DDLConfig ddlConfig = new DDLConfig();
        logger.info("source ddl: {}", sql);
        Statement statement = CCJSqlParserUtil.parse(sql);
        
        // 提前返回：非 Alter 语句直接返回
        if (!(statement instanceof Alter)) {
            logger.warn("DDL statement is not Alter: " + statement);
            return ddlConfig;
        }
        
        Alter alter = (Alter) statement;

        // 统一获取源和目标连接器配置
        Mapping mapping = tableGroup.profileComponent.getMapping(tableGroup.getMappingId());
        ConnectorConfig sourceConnectorConfig = tableGroup.profileComponent
                .getConnector(mapping.getSourceConnectorId()).getConfig();
        ConnectorConfig targetConnectorConfig = tableGroup.profileComponent
                .getConnector(mapping.getTargetConnectorId()).getConfig();
        String sourceConnectorType = sourceConnectorConfig.getConnectorType();
        String targetConnectorType = targetConnectorConfig.getConnectorType();

        // 获取目标表名
        String targetTableName = tableGroup.getTargetTable().getName();

        // 从源连接器获取源到IR转换器（用于统一处理操作类型映射）
        ConnectorService sourceConnectorService = connectorFactory.getConnectorService(sourceConnectorConfig);
        SourceToIRConverter sourceToIRConverter = sourceConnectorService.getSourceToIRConverter();

        // 异构：源端和目标端类型不同（包括数据库和非数据库连接器）
        boolean isHeterogeneous = !StringUtil.equals(sourceConnectorType, targetConnectorType);
        boolean hasChangeOperation = hasChangeOperation(alter);
        // 异构或包含CHANGE操作：需要IR转换
        boolean needIRConversion = isHeterogeneous || hasChangeOperation;
        
        // 统一调用 SourceToIRConverter.convert 来解析操作类型（所有连接器都需要）
        // 对于异构或包含CHANGE操作，同时保存IR用于后续SQL转换，避免重复调用
        DDLIntermediateRepresentation ir = convertToIR(sourceToIRConverter, alter, targetTableName, needIRConversion);

        // 统一使用策略模式提取字段信息（所有连接器都需要）
        extractFieldInfo(alter, ddlConfig);

        // SQL转换：异构或包含CHANGE操作时使用IR转换，否则使用原生SQL（仅替换表名）
        String targetSql;
        if (needIRConversion) {
            // 异构或包含CHANGE操作：使用IR转换
            String targetSchema = connectorService instanceof Database 
                    ? extractTargetSchema(targetConnectorConfig) 
                    : null;
            targetSql = convertSqlUsingIR(connectorService, sourceConnectorService, sourceToIRConverter, ir,
                    targetTableName, targetSchema, sourceConnectorType, targetConnectorType);
        } else {
            // 同构数据库且无CHANGE操作：使用原生SQL，仅替换表名
            String targetSchema = extractTargetSchema(targetConnectorConfig);
            targetSql = convertSqlByReplacingTableName(connectorService, alter, sql, targetTableName, targetSchema);
        }
        logger.info("target ddl: {}", targetSql);

        ddlConfig.setSql(targetSql);
        return ddlConfig;
    }

    /**
     * 检查是否有 CHANGE 操作
     */
    private boolean hasChangeOperation(Alter alter) {
        if (alter.getAlterExpressions() == null) {
            return false;
        }
        return alter.getAlterExpressions().stream()
                .anyMatch(expr -> expr.getOperation() == AlterOperation.CHANGE);
    }

    /**
     * 转换为中间表示（IR）
     */
    private DDLIntermediateRepresentation convertToIR(SourceToIRConverter sourceToIRConverter, Alter alter,
                                                      String targetTableName, boolean needIRConversion) {
        if (sourceToIRConverter == null) {
            return null;
        }

        if (needIRConversion) {
            // 异构数据库或包含CHANGE操作：保存IR用于后续SQL转换
            alter.getTable().setName(targetTableName);
            return sourceToIRConverter.convert(alter);
        } else {
            // 同构数据库或无CHANGE操作：仅调用convert来修改操作类型，供策略模式使用
            sourceToIRConverter.convert(alter);
            return null;
        }
    }

    /**
     * 使用策略模式提取字段信息
     */
    private void extractFieldInfo(Alter alter, DDLConfig ddlConfig) {
        for (AlterExpression expression : alter.getAlterExpressions()) {
            STRATEGIES.computeIfPresent(expression.getOperation(), (k, strategy) -> {
                strategy.parse(expression, ddlConfig);
                return strategy;
            });
        }
    }


    /**
     * 提取目标数据库的 schema
     */
    private String extractTargetSchema(ConnectorConfig targetConnectorConfig) {
        if (!(targetConnectorConfig instanceof org.dbsyncer.sdk.config.DatabaseConfig)) {
            return null;
        }
        org.dbsyncer.sdk.config.DatabaseConfig targetDatabaseConfig =
                (org.dbsyncer.sdk.config.DatabaseConfig) targetConnectorConfig;
        return targetDatabaseConfig.getSchema();
    }

    /**
     * 使用IR进行SQL转换（异构或包含CHANGE操作）
     */
    private String convertSqlUsingIR(ConnectorService connectorService, ConnectorService sourceConnectorService,
                                     SourceToIRConverter sourceToIRConverter, DDLIntermediateRepresentation ir, 
                                     String targetTableName, String targetSchema,
                                     String sourceConnectorType, String targetConnectorType) {
        // 对于非数据库连接器（如 Kafka），使用源端连接器的转换器将标准化的 IR 转换为标准 SQL
        // 标准化过程由源端连接器完成（通过 SourceToIRConverter），非数据库连接器只需要将 IR 转换为标准 SQL
        IRToTargetConverter irToTargetConverter = connectorService instanceof Database
                ? connectorService.getIRToTargetConverter()
                : sourceConnectorService.getIRToTargetConverter();

        if (sourceToIRConverter == null || irToTargetConverter == null) {
            throw new IllegalArgumentException(
                    String.format("无法获取从 %s 到 %s 的转换器", sourceConnectorType, targetConnectorType));
        }

        ir.setTableName(targetTableName);
        ir.setSchema(targetSchema);
        return irToTargetConverter.convert(ir);
    }

    /**
     * 通过替换表名进行SQL转换（同构数据库且无CHANGE操作）
     */
    private String convertSqlByReplacingTableName(ConnectorService connectorService, Alter alter, String sql,
                                                  String targetTableName, String targetSchema) {
        String originalTableName = alter.getTable().getName();
        String originalSchemaName = alter.getTable().getSchemaName();

        Database targetDatabase = (Database) connectorService;
        SqlTemplate targetSqlTemplate = targetDatabase.getSqlTemplate();

        if (targetSqlTemplate != null) {
            return targetSqlTemplate.replaceTableNameInSql(sql, originalTableName,
                    originalSchemaName, targetTableName, targetSchema);
        }

        // 回退方案：简单字符串替换
        logger.warn("目标数据库连接器未提供SqlTemplate，使用简单字符串替换表名");
        String originalTableNameWithSchema = buildTableNameWithSchema(originalSchemaName, originalTableName);
        String targetTableNameWithSchema = buildTableNameWithSchema(targetSchema, targetTableName);
        return sql.replace(originalTableNameWithSchema, targetTableNameWithSchema);
    }

    /**
     * 构建带schema的表名
     */
    private String buildTableNameWithSchema(String schema, String tableName) {
        return (schema != null && !schema.trim().isEmpty())
                ? schema + "." + tableName
                : tableName;
    }

    /**
     * 刷新 Table 的字段元数据（收敛自 MetaBufferActuator 第 7 步）
     * ADR-0011: Table 是唯一的元数据源，刷新 FieldMapping 前必须保证 Table 最新
     */
    private void refreshTableColumns(TableGroup tableGroup) {
        try {
            Mapping mapping = tableGroup.profileComponent.getMapping(tableGroup.getMappingId());
            ConnectorConfig sourceConfig = tableGroup.profileComponent
                    .getConnector(mapping.getSourceConnectorId()).getConfig();
            ConnectorConfig targetConfig = tableGroup.profileComponent
                    .getConnector(mapping.getTargetConnectorId()).getConfig();

            ConnectorInstance sourceInstance = connectorFactory.connect(sourceConfig);
            MetaInfo sourceMeta = connectorFactory.getMetaInfo(sourceInstance, tableGroup.getSourceTable().getName());
            tableGroup.getSourceTable().setColumn(sourceMeta.getColumn());

            ConnectorInstance targetInstance = connectorFactory.connect(targetConfig);
            MetaInfo targetMeta = connectorFactory.getMetaInfo(targetInstance, tableGroup.getTargetTable().getName());
            tableGroup.getTargetTable().setColumn(targetMeta.getColumn());

            if (CollectionUtils.isEmpty(tableGroup.getTargetTable().getColumn())) {
                tableGroup.getTargetTable().setColumn(new ArrayList<>(tableGroup.getSourceTable().getColumn()));
            }
        } catch (Exception e) {
            logger.warn("刷新表元数据失败，使用现有 Table 字段，table={}", tableGroup.getTargetTable().getName(), e);
        }
    }

    @Override
    public void refreshFiledMappings(TableGroup tableGroup, DDLConfig targetDDLConfig) {
        // ADR-0011: FieldMapping 依赖 Table 中的 Field 元数据，此处显式刷新以保证自洽
        refreshTableColumns(tableGroup);

        switch (targetDDLConfig.getDdlOperationEnum()) {
            case ALTER_MODIFY:
                updateFieldMapping(tableGroup, targetDDLConfig.getModifiedFieldNames());
                break;
            case ALTER_ADD:
                appendFieldMappings(tableGroup, targetDDLConfig.getAddedFieldNames());
                break;
            case ALTER_CHANGE:
                renameFieldMapping(tableGroup, targetDDLConfig.getChangedFieldNames());
                break;
            case ALTER_DROP:
                removeFieldMappings(tableGroup, targetDDLConfig.getDroppedFieldNames());
                break;
            default:
                logger.error("不支持的DDL操作类型: {}, DDL SQL: {}, 无法更新字段映射",
                        targetDDLConfig.getDdlOperationEnum(), targetDDLConfig.getSql());
                throw new RuntimeException("不支持的DDL操作类型");
        }
    }

    private void updateFieldMapping(TableGroup tableGroup, List<String> modifiedFieldNames) {
        // ADR-0011: FieldMapping 只存字段名，元数据从 Table 动态获取
        // MODIFY COLUMN 场景下字段名不变，无需额外处理
        // 元数据已通过 initCommand() 中 findColumnByName 自动更新
    }

    private void appendFieldMappings(TableGroup tableGroup, List<String> addedFieldNames) {
        if (CollectionUtils.isEmpty(addedFieldNames)) {
            return;
        }

        for (String addedFieldName : addedFieldNames) {
            Field source = tableGroup.getSourceTable().findColumnByName(addedFieldName);
            Field target = tableGroup.getTargetTable().findColumnByName(addedFieldName);
            if (source != null && target != null) {
                // ADR-0011: 使用 Field 构造函数，内部提取字段名
                tableGroup.getFieldMapping().add(new FieldMapping(source, target));
            }
        }
    }

    private void renameFieldMapping(TableGroup tableGroup, Map<String, String> changedFieldNames) {
        if (changedFieldNames.isEmpty()) {
            return;
        }

        Set<Map.Entry<String, String>> entries = changedFieldNames.entrySet();
        Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
        while (iterator.hasNext()) {
            FieldMapping fieldMapping = iterator.next();
            String sourceName = fieldMapping.getSourceName();
            String targetName = fieldMapping.getTargetName();

            for (Map.Entry<String, String> entry : entries) {
                String oldName = entry.getKey();
                String newName = entry.getValue();

                // 只处理源字段名匹配的情况（changedFieldNames 中的键是源数据库的字段名）
                if (fieldMapping.matchesSource(oldName)) {
                    Field newSourceField = tableGroup.getSourceTable().findColumnByName(newName);
                    if (newSourceField == null) {
                        logger.warn("源表中未找到新字段 {}，移除字段映射", newName);
                        iterator.remove();
                        break; // 已移除，跳过后续处理
                    }

                    // 更新源字段名
                    fieldMapping.setSourceName(newName);

                    // 如果源字段名和目标字段名相同，则同时更新目标字段
                    // 这符合常见场景：同名字段应该同步更新
                    if (fieldMapping.matchesTarget(oldName)) {
                        Field newTargetField = tableGroup.getTargetTable().findColumnByName(newName);
                        if (newTargetField != null) {
                            fieldMapping.setTargetName(newName);
                            logger.debug("更新字段映射: {} -> {} (源和目标字段名相同，同时更新)", oldName, newName);
                        } else {
                            logger.warn("目标表中未找到新字段 {}，但保留字段映射（源字段已更新）", newName);
                        }
                    } else {
                        // 源字段名和目标字段名不同（自定义映射），只更新源字段
                        logger.debug("更新字段映射源字段: {} -> {} (目标字段名不同，仅更新源)", oldName, newName);
                    }
                }
            }
        }
    }

    private void removeFieldMappings(TableGroup tableGroup, List<String> droppedFieldNames) {
        if (CollectionUtils.isEmpty(droppedFieldNames)) {
            return;
        }

        Iterator<FieldMapping> iterator = tableGroup.getFieldMapping().iterator();
        while (iterator.hasNext()) {
            FieldMapping fieldMapping = iterator.next();
            String sourceName = fieldMapping.getSourceName();
            String targetName = fieldMapping.getTargetName();
            for (String droppedFieldName : droppedFieldNames) {
                if (fieldMapping.matchesSource(droppedFieldName)
                        || fieldMapping.matchesTarget(droppedFieldName)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }
}