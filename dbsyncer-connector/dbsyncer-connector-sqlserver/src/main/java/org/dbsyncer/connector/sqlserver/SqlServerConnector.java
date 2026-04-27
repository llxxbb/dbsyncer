/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.sqlserver;

import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.sqlserver.bulk.SqlServerBulkCopyUtil;
import org.dbsyncer.connector.sqlserver.cdc.Lsn;
import org.dbsyncer.connector.sqlserver.cdc.SqlServerListener;
import org.dbsyncer.connector.sqlserver.converter.IRToSQLServerConverter;
import org.dbsyncer.connector.sqlserver.converter.SQLServerToIRConverter;
import org.dbsyncer.connector.sqlserver.schema.SqlServerSchemaResolver;
import org.dbsyncer.connector.sqlserver.validator.SqlServerConfigValidator;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.connector.database.ds.SimpleConnection;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.connector.database.sql.impl.SqlServerTemplate;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.enums.TableTypeEnum;
import org.dbsyncer.sdk.listener.DatabaseQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.schema.SchemaResolver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SqlServer连接器实现
 */
public class SqlServerConnector extends AbstractDatabaseConnector {


    private final String QUERY_VIEW = "select name from sysobjects where xtype in('v')";
    private final String QUERY_TABLE = "select name from sys.tables where schema_id = schema_id('%s') and is_ms_shipped = 0";
    private final String MARK_HAS_IDENTITY = "mark.hasIdentity";

    private final SqlServerSchemaResolver schemaResolver = new SqlServerSchemaResolver();
    private final SqlServerBulkCopyUtil sqlServerBulkCopyUtil;

    public SqlServerConnector() {
        sqlTemplate = new SqlServerTemplate(schemaResolver);
        configValidator = new SqlServerConfigValidator();
        sourceToIRConverter = new SQLServerToIRConverter();
        irToTargetConverter = new IRToSQLServerConverter(sqlTemplate);
        sqlServerBulkCopyUtil = new SqlServerBulkCopyUtil(sqlTemplate, schemaResolver);
    }

    @Override
    public String getConnectorType() {
        return "SqlServer";
    }


    @Override
    public List<Table> getTable(DatabaseConnectorInstance connectorInstance) throws Exception {
        DatabaseConfig config = connectorInstance.getConfig();
        List<Table> tables = getTables(connectorInstance, String.format(QUERY_TABLE, config.getSchema()), TableTypeEnum.TABLE);
        tables.addAll(getTables(connectorInstance, QUERY_VIEW, TableTypeEnum.VIEW));
        return tables;
    }

    @Override
    public Listener getListener(String listenerType) {
        if (ListenerTypeEnum.isTiming(listenerType)) {
            return new DatabaseQuartzListener();
        }

        if (ListenerTypeEnum.isLog(listenerType)) {
            return new SqlServerListener();
        }
        return null;
    }

    private List<Table> getTables(DatabaseConnectorInstance connectorInstance, String sql, TableTypeEnum type) throws Exception {
        List<String> tableNames = connectorInstance.execute(databaseTemplate -> databaseTemplate.queryForList(sql, String.class));
        if (!CollectionUtils.isEmpty(tableNames)) {
            return tableNames.stream().map(name -> new Table(name, type.getCode())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public Map<String, String> getTargetCommand(CommandConfig commandConfig) throws Exception {
        Map<String, String> targetCommand = super.getTargetCommand(commandConfig);
        
        // 从源表的字段信息中判断是否包含IDENTITY列
        // 不再需要查询目标端，直接使用源表的autoincrement属性
        Table targetTable = commandConfig.getTable();

        assert targetTable.getColumn() != null;
        for (Field field : targetTable.getColumn()) {
            if (field.isAutoincrement()) {
                targetCommand.put(MARK_HAS_IDENTITY, String.valueOf(true));
                break;
            }
        }
        
        return targetCommand;
    }

    @Override
    public Map<String, String> getPosition(DatabaseConnectorInstance connectorInstance) throws Exception {
        // 查询当前LSN位置
        byte[] currentLsnBytes = connectorInstance.execute(databaseTemplate ->
                databaseTemplate.queryForObject("SELECT sys.fn_cdc_get_max_lsn()", byte[].class));

        if (currentLsnBytes == null) {
            throw new RuntimeException("获取SqlServer当前LSN失败");
        }

        // 将 byte[] 转换为 LSN 字符串表示
        String currentLsn = new Lsn(currentLsnBytes).toString();

        // 创建与snapshot中存储格式一致的position信息
        Map<String, String> position = new HashMap<>();
        position.put("position", currentLsn);
        return position;
    }

    @Override
    public Integer getStreamingFetchSize(ReaderContext context) {
        return context.getPageSize(); // 使用页面大小作为fetchSize
    }

    @Override
    public SchemaResolver getSchemaResolver() {
        return schemaResolver;
    }

    // 重要说明 mssql-jdbc 9 +  useBulkCopyForBatchInsert=true 原生驱动 Buck insert 无效，性能非常慢，需要定制实现

    /**
     * 重写 insert 方法，对 SQL Server 使用批量复制优化
     */
    @Override
    public Result insert(DatabaseConnectorInstance connectorInstance, PluginContext context) {
        // 调用父类 executeWriter()，使用统一的 CT 删除异常处理（ADR 05）
        return executeWriter(connectorInstance, context, context.getTargetFields(), 0);
    }

    @Override
    public Result upsert(DatabaseConnectorInstance connectorInstance, PluginContext context) {
        // 调用父类 executeWriter()，使用统一的 CT 删除异常处理（ADR 05）
        return executeWriter(connectorInstance, context, context.getTargetFields(), 0);
    }

    /**
     * 重写批次执行方法，使用 SqlServerBulkCopyUtil 进行高效批量操作
     * - INSERT/UPSERT：使用 SqlServerBulkCopyUtil
     * - UPDATE/DELETE：回退父类默认实现（JdbcTemplate.batchUpdate）
     */
    @Override
    protected int[] doExecuteBatch(DatabaseConnectorInstance connectorInstance, PluginContext context,
                                   List<Field> fields, List<Map> data) throws Exception {
        String event = context.getEvent();
        
        // 只对 INSERT 和 UPSERT 使用 SqlServerBulkCopyUtil
        if ("insert".equals(event) || "upsert".equals(event)) {
            return connectorInstance.execute(databaseTemplate -> {
                SimpleConnection connection = databaseTemplate.getSimpleConnection();
                String schemaName = connectorInstance.getConfig().getSchema();
                if (schemaName == null || schemaName.trim().isEmpty()) {
                    schemaName = "dbo";
                }
                boolean enableIdentityInsert = Boolean.parseBoolean(context.getCommand().get(MARK_HAS_IDENTITY));
                String tableName = context.getTargetTableName();
                
                if ("insert".equals(event)) {
                    // INSERT 操作
                    List<Map<String, Object>> typedData = buildTypedData(data);
                    sqlServerBulkCopyUtil.bulkInsert(connection, tableName, fields, typedData, schemaName, enableIdentityInsert);
                } else {
                    // UPSERT 操作
                    List<String> primaryKeys = findPrimaryKeys(fields);
                    if (primaryKeys.isEmpty()) {
                        logger.warn("表 {} 没有主键，无法执行 UPSERT，回退到普通插入", tableName);
                        List<Map<String, Object>> typedData = buildTypedData(data);
                        sqlServerBulkCopyUtil.bulkInsert(connection, tableName, fields, typedData, schemaName, enableIdentityInsert);
                    } else {
                        List<Map<String, Object>> typedData = buildTypedData(data);
                        sqlServerBulkCopyUtil.bulkUpsert(connection, tableName, fields, typedData, primaryKeys, schemaName, enableIdentityInsert);
                    }
                }
                
                // 返回 int[] 数组，每个元素为 1 表示成功
                return buildSuccessArray(data.size());
            });
        } else {
            // UPDATE 或 DELETE：回退到父类默认实现（JdbcTemplate.batchUpdate）
            return super.doExecuteBatch(connectorInstance, context, fields, data);
        }
    }
    
    /**
     * 构建类型化数据列表
     */
    private List<Map<String, Object>> buildTypedData(List<Map> data) {
        List<Map<String, Object>> typedData = new java.util.ArrayList<>();
        for (Map map : data) {
            typedData.add((Map<String, Object>) map);
        }
        return typedData;
    }
    
    /**
     * 查找主键字段列表
     */
    private List<String> findPrimaryKeys(List<Field> fields) {
        return fields.stream()
                .filter(Field::isPk)
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 构建成功结果数组
     */
    private int[] buildSuccessArray(int size) {
        int[] result = new int[size];
        Arrays.fill(result, 1);
        return result;
    }

    /**
     * 生成创建目标表的 DDL
     * 
     * @param sourceMetaInfo 源表元数据
     * @param targetTableName 目标表名称
     * @param primaryKeys 主键列表（逗号分隔），必须传递
     * @return CREATE TABLE DDL
     */
    public String generateCreateTableDDL(MetaInfo sourceMetaInfo, String targetTableName, String primaryKeys) {
        SqlTemplate sqlTemplate = this.sqlTemplate;
        if (sqlTemplate == null) {
            throw new UnsupportedOperationException("SQL Server 连接器不支持自动生成 CREATE TABLE DDL");
        }

        // 主键配置必须传递，确保 100% 正确性
        if (StringUtil.isBlank(primaryKeys)) {
            throw new IllegalArgumentException("主键配置不能为空");
        }

        // 从 MetaInfo 中提取字段列表
        List<Field> fields = sourceMetaInfo.getColumn();
        
        // 使用用户指定的主键顺序（100% 正确性）
        List<String> primaryKeysList = new ArrayList<>();
        for (String pk : StringUtil.split(primaryKeys, StringUtil.COMMA)) {
            primaryKeysList.add(pk.trim());
        }

        // 调用 SqlTemplate 的 buildCreateTableSql 方法进行 SQL 模板组装
        StringBuilder ddl = new StringBuilder();
        ddl.append(sqlTemplate.buildCreateTableSql(null, targetTableName, fields, primaryKeysList));
        
        // 如果有 COMMENT，追加 COMMENT 语句（使用分号分隔，作为独立的 SQL 语句）
        String effectiveSchema = "dbo";
        List<Field> fieldsWithComment = new ArrayList<>();
        for (Field field : fields) {
            if (field.getComment() != null && !field.getComment().trim().isEmpty()) {
                fieldsWithComment.add(field);
            }
        }
        
        if (!fieldsWithComment.isEmpty() && sqlTemplate instanceof org.dbsyncer.sdk.connector.database.sql.impl.SqlServerTemplate) {
            org.dbsyncer.sdk.connector.database.sql.impl.SqlServerTemplate sqlServerTemplate = 
                    (org.dbsyncer.sdk.connector.database.sql.impl.SqlServerTemplate) sqlTemplate;
            for (Field field : fieldsWithComment) {
                ddl.append("; ");
                ddl.append(sqlServerTemplate.buildCommentSql(effectiveSchema, targetTableName, field.getName(), field.getComment()));
            }
        }
        
        return ddl.toString();
    }

    @Override
    protected CatalogAndSchema resolveEffectiveCatalogAndSchema(Connection conn, String catalog, String schema) throws SQLException {
        // SQL Server: catalog=数据库名，schema=如 dbo
        String effectiveCatalog = (catalog != null) ? catalog : conn.getCatalog();
        String effectiveSchema = (schema != null) ? schema : "dbo";
        return new CatalogAndSchema(effectiveCatalog, effectiveSchema);
    }

    @Override
    protected String resolveFieldType(String typeName, Connection connection, String schemaName) throws SQLException {
        if (typeName == null || typeName.trim().isEmpty()) {
            return typeName;
        }

        String upperTypeName = typeName.toUpperCase();
        
        if (schemaResolver.getSupportedTypeNames().contains(upperTypeName)) {
            return typeName;
        }

        String baseType = queryUserDefinedTypeBase(upperTypeName, connection, schemaName);
        if (baseType != null) {
            logger.info("自动转换用户定义类型 [{}] -> [{}]", typeName, baseType);
            return baseType;
        }

        return typeName;
    }


    private String queryUserDefinedTypeBase(String typeName, Connection connection, String schemaName) throws SQLException {
        String sql = 
            "SELECT st.name AS base_type " +
            "FROM sys.types ty " +
            "INNER JOIN sys.types st ON ty.system_type_id = st.user_type_id " +
            "WHERE ty.name = ? AND ty.is_user_defined = 1";
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, typeName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("base_type");
                }
            }
        }
        
        return null;
    }

    @Override
    public boolean supportsFieldDifference() {
        return true;
    }
}