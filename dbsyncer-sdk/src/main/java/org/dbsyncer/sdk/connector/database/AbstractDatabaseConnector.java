package org.dbsyncer.sdk.connector.database;

import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.AbstractConnector;
import org.dbsyncer.sdk.connector.ConfigValidator;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.database.ds.SimpleConnection;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.connector.database.sql.context.SqlBuildContext;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.enums.FilterEnum;
import org.dbsyncer.sdk.enums.OperationEnum;
import org.dbsyncer.sdk.enums.QuartzFilterEnum;
import org.dbsyncer.sdk.enums.TableTypeEnum;
import org.dbsyncer.sdk.model.*;
import org.dbsyncer.sdk.parser.ddl.converter.IRToTargetConverter;
import org.dbsyncer.sdk.parser.ddl.converter.SourceToIRConverter;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.dbsyncer.sdk.listener.AbstractListener;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.util.DatabaseUtil;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.support.rowset.ResultSetWrappingSqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.util.Assert;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 关系型数据库连接器实现
 */
public abstract class AbstractDatabaseConnector extends AbstractConnector
        implements ConnectorService<DatabaseConnectorInstance, DatabaseConfig>, Database {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    public SqlTemplate sqlTemplate;

    public SourceToIRConverter sourceToIRConverter;

    public IRToTargetConverter irToTargetConverter;

    /**
     * 是否为DQL连接器
     * 默认为false，DQL连接器在初始化时设置为true
     */
    protected boolean isDql = false;

    /**
     * 配置验证器
     * 子类可以覆盖此字段来提供特定的验证器
     */
    protected ConfigValidator<?> configValidator = null;

    @Override
    public Class<DatabaseConfig> getConfigClass() {
        return DatabaseConfig.class;
    }

    @Override
    public ConnectorInstance connect(DatabaseConfig config) {
        return new DatabaseConnectorInstance(config);
    }

    @Override
    public void disconnect(DatabaseConnectorInstance connectorInstance) {
        connectorInstance.close();
    }

    @Override
    public boolean isAlive(DatabaseConnectorInstance connectorInstance) throws Exception {
        Integer count = connectorInstance
                .execute(databaseTemplate -> databaseTemplate.queryForObject(getValidationQuery(), Integer.class));
        return null != count && count > 0;
    }

    @Override
    public String getConnectorInstanceCacheKey(DatabaseConfig config) {
        return String.format("%s-%s-%s", config.getConnectorType(), config.getUrl(), config.getUsername());
    }

    @Override
    public ConfigValidator<?> getConfigValidator() {
        return configValidator;
    }

    @Override
    public List<Table> getTable(DatabaseConnectorInstance connectorInstance) throws Exception {
        // DQL模式：从配置中获取用户定义的SQL表
        if (isDql) {
            DatabaseConfig cfg = connectorInstance.getConfig();
            List<SqlTable> sqlTables = cfg.getSqlTables();
            List<Table> tables = new ArrayList<>();
            if (!CollectionUtils.isEmpty(sqlTables)) {
                sqlTables.forEach(s -> tables.add(new Table(s.getSqlName(), TableTypeEnum.TABLE.getCode(),
                        Collections.EMPTY_LIST, s.getSql(), null)));
            }
            return tables;
        }
        // 非DQL模式：查询数据库元数据获取表列表
        return getTable(connectorInstance, null, getSchema(connectorInstance.getConfig()), null);
    }

    @Override
    public MetaInfo getMetaInfo(DatabaseConnectorInstance connectorInstance, String tableNamePattern) throws Exception {
        // DQL模式：通过执行SQL获取元数据
        if (isDql) {
            DatabaseConfig cfg = connectorInstance.getConfig();
            List<SqlTable> sqlTables = cfg.getSqlTables();
            for (SqlTable s : sqlTables) {
                if (StringUtil.equals(s.getSqlName(), tableNamePattern)) {
                    String sql = s.getSql().toUpperCase();
                    sql = sql.replace("\t", " ");
                    sql = sql.replace("\r", " ");
                    sql = sql.replace("\n", " ");
                    String queryMetaSql = StringUtil.contains(sql, " WHERE ") ? s.getSql() + " AND 1!=1 "
                            : s.getSql() + " WHERE 1!=1 ";
                    try {
                        return connectorInstance.execute(databaseTemplate -> getMetaInfo(databaseTemplate, queryMetaSql,
                                getSchema(cfg), s.getTable()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return null;
        }
        // 非DQL模式：直接查询表结构元数据
        List<Field> fields = new ArrayList<>();
        final String schema = getSchema(connectorInstance.getConfig());
        List<String> primaryKeys = new ArrayList<>();
        connectorInstance.execute(databaseTemplate -> {
            SimpleConnection connection = databaseTemplate.getSimpleConnection();
            Connection conn = connection.getConnection();
            String catalog = conn.getCatalog();
            String schemaNamePattern = null == schema ? conn.getSchema() : schema;
            DatabaseMetaData metaData = conn.getMetaData();
            primaryKeys.addAll(findTablePrimaryKeys(metaData, catalog, schemaNamePattern, tableNamePattern));
            try (ResultSet columnMetadata = metaData.getColumns(catalog, schemaNamePattern, tableNamePattern, null)) {
                while (columnMetadata.next()) {
                    String columnName = columnMetadata.getString("COLUMN_NAME");
                    int columnType = columnMetadata.getInt("DATA_TYPE");
                    String typeName = columnMetadata.getString("TYPE_NAME");
                    long columnSize = Math.max(0L, columnMetadata.getLong("COLUMN_SIZE"));
                    int ratio = Math.max(0, columnMetadata.getInt("DECIMAL_DIGITS"));

                    typeName = resolveFieldType(typeName, conn, schemaNamePattern);

                    Field field = new Field(columnName, typeName, columnType,
                            primaryKeys.stream().anyMatch(pk -> StringUtil.equalsIgnoreCase(pk, columnName)),
                            columnSize, ratio);

                    // 填充 nullable 属性
                    int nullable = columnMetadata.getInt("NULLABLE");
                    if (nullable == ResultSetMetaData.columnNoNulls) {
                        field.setNullable(false); // NOT NULL
                    } else if (nullable == ResultSetMetaData.columnNullable) {
                        field.setNullable(true); // NULL
                    }
                    // columnNullableUnknown 时保持 null，表示未知

                    // 注意：不再读取和设置 defaultValue，因为数据同步不需要默认值支持

                    // 填充 comment 属性
                    String remarks = columnMetadata.getString("REMARKS");
                    if (remarks != null && !remarks.trim().isEmpty()) {
                        field.setComment(remarks);
                    }

                    // 填充 autoincrement 属性
                    try {
                        processAutoIncrement(field, columnMetadata);
                    } catch (SQLException e) {
                        // 如果数据库驱动不支持 IS_AUTOINCREMENT 列，则默认为 false
                        // 某些旧版本的数据库驱动可能不支持此列
                        logger.warn("数据库驱动不支持 IS_AUTOINCREMENT 列，字段={}，默认设置为 false", field.getName(), e);
                        field.setAutoincrement(false);
                    }

                    fields.add(field);
                }
            }
            return fields;
        });
        if (CollectionUtils.isEmpty(fields)) {
            return null;
        }
        MetaInfo result = new MetaInfo()
                .setColumn(enhanceFields(connectorInstance, fields, tableNamePattern))
                .setPrimaryKeys(primaryKeys);
        result.setName(tableNamePattern);
        return result;
    }

    /**
     * 处理自增字段属性（从 DatabaseMetaData 的 IS_AUTOINCREMENT 列）
     * 如果数据库驱动不支持该列，抛出 SQLException
     *
     * @param field          字段对象
     * @param columnMetadata 列元数据 ResultSet
     * @throws SQLException 数据库访问异常
     */
    private void processAutoIncrement(Field field, ResultSet columnMetadata) throws SQLException {
        String isAutoIncrement = columnMetadata.getString("IS_AUTOINCREMENT");
        if ("YES".equalsIgnoreCase(isAutoIncrement)) {
            field.setAutoincrement(true);
        } else {
            field.setAutoincrement(false);
        }
    }

    /**
     * 增强字段信息（子类可重写以补充特定数据库的元数据）
     * 默认实现直接返回原字段列表，不做任何修改
     *
     * @param connectorInstance 连接器实例
     * @param fields            基础字段列表（已从 JDBC DatabaseMetaData 获取）
     * @param tableName         表名
     * @return 增强后的字段列表
     * @throws Exception 增强过程中可能的异常
     */
    protected List<Field> enhanceFields(DatabaseConnectorInstance connectorInstance, List<Field> fields,
            String tableName) throws Exception {
        return fields;
    }

    protected String resolveFieldType(String typeName, Connection connection, String schemaName) throws SQLException {
        return typeName;
    }

    @Override
    public long getCount(DatabaseConnectorInstance connectorInstance, Map<String, String> command) throws Exception {
        if (CollectionUtils.isEmpty(command)) {
            return 0L;
        }

        // 1、获取select SQL
        String queryCountSql = command.get(ConnectorConstant.OPERTION_QUERY_COUNT);
        if (StringUtil.isBlank(queryCountSql)) {
            return 0;
        }

        // 2、返回结果集
        try {
            return connectorInstance.execute(databaseTemplate -> {
                Long count = databaseTemplate.queryForObject(queryCountSql, Long.class);
                return count == null ? 0 : count;
            });
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    @Override
    public Result reader(DatabaseConnectorInstance connectorInstance, ReaderContext context) {
        throw new RuntimeException("AbstractDatabaseConnector.reader 需要重构.");
        // // 1、获取select SQL
        // boolean supportedCursor = null != context.getCursors();
        // String queryKey = supportedCursor ? ConnectorConstant.OPERTION_QUERY_CURSOR :
        // ConnectorConstant.OPERTION_QUERY_STREAM;
        // final String querySql = context.getCommand().get(queryKey);
        // Assert.hasText(querySql, "查询语句不能为空.");
        //
        // // 2、设置参数
        // Collections.addAll(context.getArgs(), supportedCursor ?
        // getPageCursorArgs(context) : new Object[]{});
        //
        // // 3、执行SQL
        // List<Map<String, Object>> list = connectorInstance.execute(databaseTemplate
        // -> databaseTemplate.queryForList(querySql, context.getArgs().toArray()));
        //
        // // 4、返回结果集
        // return new Result(list);
    }

    @Override
    public Result writer(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows) {
        return executeWriter(connectorInstance, syncRows, syncRows.get(0).getTargetFields());
    }

    @Override
    public Result insert(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows) {
        List<Field> fields = new ArrayList<>(syncRows.get(0).getTargetFields());
        return executeWriter(connectorInstance, syncRows, fields);
    }

    @Override
    public Result update(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows) {
        // 对于UPDATE操作，保留所有字段在SET子句中（包括主键字段）
        // 这样支持主键修改场景：SET pk = new_value WHERE ...
        List<Field> fields = new ArrayList<>(syncRows.get(0).getTargetFields());
        return executeWriter(connectorInstance, syncRows, fields);
    }

    @Override
    public Result delete(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows) {
        List<Field> pkFields = syncRows.get(0).getTableGroup().getTargetTable().getPkFields();
        if (CollectionUtils.isEmpty(pkFields)) {
            throw new SdkException("Primary keys cannot be empty for DELETE operation");
        }
        return executeWriter(connectorInstance, syncRows, pkFields);
    }

    @Override
    public Result upsert(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows) {
        List<Field> fields = new ArrayList<>(syncRows.get(0).getTargetFields());
        return executeWriter(connectorInstance, syncRows, fields);
    }

    /**
     * 执行写入操作（带重试支持）
     */
    protected Result executeWriter(DatabaseConnectorInstance connectorInstance, List<SyncRow> syncRows,
            List<Field> fields) {
        if (syncRows.isEmpty()) {
            throw new SdkException("writer data can not be empty.");
        }
        if (CollectionUtils.isEmpty(fields)) {
            throw new SdkException("writer fields can not be empty.");
        }

        int[] execute;
        try {
            execute = doExecuteBatch(connectorInstance, fields, syncRows);
        } catch (Exception e) {
            throw new SdkException(e);
        }

        Result result = new Result();
        for (int i = 0; i < execute.length; i++) {
            result.getSuccessData().add(syncRows.get(i).getData());
            int affectedRows = execute[i];
            SyncRow row = syncRows.get(i);
            String rowEvent = row.getContext() != null ? row.getContext().getEvent() : StringUtil.EMPTY;
            if (affectedRows == 1 || affectedRows == -2 || affectedRows == 0 || 
                (affectedRows == 2 && ConnectorConstant.OPERTION_UPSERT.equals(rowEvent))) {
                // 1、affectedRows == 1：表示成功执行一条记录
                // 2、affectedRows == -2：表示成功执行批量操作（JDBC驱动返回-2表示批量操作成功）
                // 3、affectedRows == 0：源端 sql server CT 模式合并DML合并导致的空操作
                // 4、affectedRows == 2 && event=UPsert：目标端 mysql 命中且改变了数据会返回2，命中但未改变数据会返回0 
            } else {
                // 记录影响行数异常，打印日志，但不写入错误队列。
                Mapping mapping = row.getMapping();
                List<String> primaryKeys = row.getPrimaryKeys();
                StringBuilder pkInfo = new StringBuilder();
                if (!CollectionUtils.isEmpty(primaryKeys) && row.getData() != null) {
                    for (String pk : primaryKeys) {
                        pkInfo.append(pk).append("=").append(row.getData().get(pk)).append(", ");
                    }
                }
                LogService logService = AbstractListener.getStaticLogService();
                if (logService != null) {
                    logService.log(mapping, LogType.SYNC, LogLevel.WARN,
                            String.format("影响行数异常: 【%d】条, event=%s, 主键值: %s", affectedRows, rowEvent, pkInfo));
                }
            }
        }
        return result;
    }

    /**
     * 执行批次操作（子类可重写以使用自定义批量执行逻辑）
     *
     * @param connectorInstance 连接器实例
     * @param fields            字段列表
     * @param syncRows          同步数据列表（携带上下文）
     * @return execute[] 数组，表示每条记录的执行结果
     * @throws Exception 执行异常
     */
    protected int[] doExecuteBatch(DatabaseConnectorInstance connectorInstance,
            List<Field> fields, List<SyncRow> syncRows) throws Exception {
        String event = syncRows.get(0).getContext().getEvent();
        String executeSql = syncRows.get(0).getTableGroup().getCommand().get(event);

        // 从 SyncRow 中提取数据用于参数绑定
        List<Map> data = new ArrayList<>(syncRows.size());
        for (SyncRow row : syncRows) {
            data.add(row.getData());
        }

        // 如果是 UPDATE 事件且有 beforeRow，需要特殊处理主键修改场景
        if (ConnectorConstant.OPERTION_UPDATE.equals(event) && hasBeforeRow(syncRows)) {
            return doExecuteBatchForUpdate(connectorInstance, fields, syncRows, executeSql);
        }

        return connectorInstance
                .execute(databaseTemplate -> databaseTemplate.batchUpdate(executeSql, batchRows(fields, data)));
    }

    /**
     * 检查是否有任何 SyncRow 包含 beforeRow
     */
    private boolean hasBeforeRow(List<SyncRow> syncRows) {
        for (SyncRow row : syncRows) {
            if (row.getBeforeRow() != null && !row.getBeforeRow().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行 UPDATE 操作，正确处理主键修改场景
     * SQL模板: UPDATE table SET non-pk-field1=?, non-pk-field2=? WHERE pk1=?, pk2=?
     * 参数绑定: [非主键新值...] + [主键旧值...]
     */
    private int[] doExecuteBatchForUpdate(DatabaseConnectorInstance connectorInstance,
            List<Field> fields, List<SyncRow> syncRows, String executeSql) throws Exception {

        return connectorInstance.execute(databaseTemplate -> {
            List<Object[]> batchArgs = new ArrayList<>();

            for (SyncRow row : syncRows) {
                List<Object> beforeRow = row.getBeforeRow();
                List<String> columnNames = row.getColumnNames();
                List<String> primaryKeys = row.getPrimaryKeys();

                // 如果没有足够的信息使用beforeRow，回退到普通逻辑
                if (beforeRow == null || beforeRow.isEmpty() ||
                        columnNames == null || columnNames.isEmpty() ||
                        primaryKeys == null || primaryKeys.isEmpty()) {
                    // 对于新SQL模板，参数数量是所有字段 + 主键
                    Object[] args = new Object[fields.size() + primaryKeys.size()];
                    int index = 0;

                    // SET子句：所有字段的新值
                    for (Field field : fields) {
                        args[index++] = row.getData().get(field.getName());
                    }

                    // WHERE子句：主键的新值（降级处理）
                    for (String pk : primaryKeys) {
                        args[index++] = row.getData().get(pk);
                    }

                    batchArgs.add(args);
                    continue;
                }

                // 构建 beforeRow 的列名到值的映射
                Map<String, Object> beforeValueMap = new java.util.HashMap<>();
                for (int i = 0; i < columnNames.size() && i < beforeRow.size(); i++) {
                    beforeValueMap.put(columnNames.get(i), beforeRow.get(i));
                }

                // SQL模板生成的参数顺序是：
                // SET子句: 所有字段（包括主键的新值）+ WHERE子句: 主键旧值
                Object[] args = new Object[fields.size() + primaryKeys.size()];
                int index = 0;

                // SET子句：所有字段使用新值（包括主键）
                for (Field field : fields) {
                    args[index++] = row.getData().get(field.getName());
                }

                // WHERE子句：主键字段使用旧值（需要在primaryKeys列表中查找对应的旧值）
                for (String pkName : primaryKeys) {
                    Object beforeValue = beforeValueMap.get(pkName);
                    if (beforeValue != null) {
                        args[index++] = beforeValue;
                    } else {
                        // 降级：如果找不到旧值，使用新值
                        args[index++] = row.getData().get(pkName);
                    }
                }

                batchArgs.add(args);
            }

            return databaseTemplate.batchUpdate(executeSql, batchArgs);
        });
    }

    @Override
    public Map<String, String> getSourceCommand(CommandConfig commandConfig) {
        MetaInfo table = commandConfig.getTable();
        List<String> primaryKeys = table.getPrimaryKeys();
        assert primaryKeys != null && !primaryKeys.isEmpty();

        String tableName = table.getName();
        assert !StringUtil.isBlank(tableName);

        // 子类实现具体的SQL生成逻辑
        return buildSourceCommands(commandConfig);
    }

    /**
     * 构建源端命令SQL
     * 各连接器实现具体的SQL生成逻辑
     *
     * @param commandConfig 命令配置
     * @return SQL命令映射
     */
    protected Map<String, String> buildSourceCommands(CommandConfig commandConfig) {
        // DQL 模式：直接使用用户自定义的 SQL
        if (isDql) {
            String queryFilterSql = getQueryFilterSql(commandConfig);
            MetaInfo table = commandConfig.getTable();
            Map<String, String> map = new HashMap<>();
            List<String> primaryKeys = table.getPrimaryKeys();
            if (CollectionUtils.isEmpty(primaryKeys)) {
                return map;
            }

            // 获取查询SQL
            String querySql = table.getSql();

            // 存在条件
            if (StringUtil.isNotBlank(queryFilterSql)) {
                querySql += queryFilterSql;
            }

            // 流式查询SQL（DQL直接使用用户自定义的SQL）
            map.put(ConnectorConstant.OPERTION_QUERY_STREAM, querySql);

            // 获取查询总数SQL
            map.put(ConnectorConstant.OPERTION_QUERY_COUNT, "SELECT COUNT(1) FROM (" + querySql + ") DBS_T");
            return map;
        }
        // 非DQL模式：子类实现具体的SQL生成逻辑
        return buildNonDqlSourceCommands(commandConfig);
    }

    /**
     * 构建非DQL模式的源端命令SQL
     * 各连接器实现具体的SQL生成逻辑
     *
     * @param commandConfig 命令配置
     * @return SQL命令映射
     */
    protected Map<String, String> buildNonDqlSourceCommands(CommandConfig commandConfig) {
        Map<String, String> map = new HashMap<>();

        // 创建构建上下文
        SqlBuildContext buildContext = sqlTemplate.createBuildContext(commandConfig, this::buildTableName,
                this::getQueryFilterSql);

        // 构建流式查询SQL
        String streamingSql = sqlTemplate.buildQueryStreamSql(buildContext);
        map.put(ConnectorConstant.OPERTION_QUERY_STREAM, streamingSql);

        // 构建游标查询SQL
        if (PrimaryKeyUtil.isSupportedCursor(commandConfig.getTable().getColumn())) {
            String cursorSql = sqlTemplate.buildQueryCursorSql(buildContext);
            map.put(ConnectorConstant.OPERTION_QUERY_CURSOR, cursorSql);
        }

        // 构建计数SQL
        String countSql = sqlTemplate.buildQueryCountSql(buildContext);
        map.put(ConnectorConstant.OPERTION_QUERY_COUNT, countSql);

        return map;
    }

    @Override
    public Map<String, String> getTargetCommand(CommandConfig commandConfig) throws Exception {
        MetaInfo table = commandConfig.getTable();
        List<String> primaryKeys = table.getPrimaryKeys();
        assert primaryKeys != null && !primaryKeys.isEmpty();

        String tableName = table.getName();
        assert tableName != null && !tableName.isEmpty();

        // 架构名
        DatabaseConfig databaseConfig = (DatabaseConfig) commandConfig.getConnectorConfig();
        // 同步字段
        List<Field> column = filterColumn(table.getColumn());

        // 构建SqlBuildContext
        SqlBuildContext buildContext = new SqlBuildContext();
        buildContext.setSchema(databaseConfig.getSchema());
        buildContext.setTableName(tableName);
        buildContext.setFields(column);
        buildContext.setPrimaryKeys(primaryKeys);

        // 使用SqlTemplate直接生成SQL
        Map<String, String> map = new HashMap<>();
        map.put(ConnectorConstant.OPERTION_INSERT, sqlTemplate.buildInsertSql(buildContext));
        map.put(ConnectorConstant.OPERTION_UPDATE, sqlTemplate.buildUpdateSql(buildContext));
        map.put(ConnectorConstant.OPERTION_DELETE, sqlTemplate.buildDeleteSql(buildContext));
        map.put(ConnectorConstant.OPERTION_UPSERT, sqlTemplate.buildUpsertSql(buildContext));
        return map;
    }

    /**
     * 获取架构名
     *
     * @param config
     * @return
     */
    protected String getSchema(DatabaseConfig config) {
        return config.getSchema();
    }

    /**
     * 获取数据库表元数据信息
     *
     * @param databaseTemplate
     * @param metaSql          查询元数据
     * @param schema           架构名
     * @param tableName        表名
     * @return
     */
    protected MetaInfo getMetaInfo(DatabaseTemplate databaseTemplate, String metaSql, String schema, String tableName)
            throws SQLException {
        SqlRowSet sqlRowSet = databaseTemplate.queryForRowSet(metaSql);
        ResultSetWrappingSqlRowSet rowSet = (ResultSetWrappingSqlRowSet) sqlRowSet;
        SqlRowSetMetaData metaData = rowSet.getMetaData();

        // 查询表字段信息
        int columnCount = metaData.getColumnCount();
        if (1 > columnCount) {
            throw new SdkException("查询表字段不能为空.");
        }
        List<Field> fields = new ArrayList<>(columnCount);
        Map<String, List<String>> tables = new HashMap<>();
        try {
            Connection connection = databaseTemplate.getSimpleConnection();
            DatabaseMetaData md = connection.getMetaData();
            final String catalog = connection.getCatalog();
            schema = StringUtil.isNotBlank(schema) ? schema : null;
            String name = null;
            String label = null;
            String typeName = null;
            String table = null;
            int columnType;
            boolean pk;
            for (int i = 1; i <= columnCount; i++) {
                table = StringUtil.isNotBlank(tableName) ? tableName : metaData.getTableName(i);
                if (null == tables.get(table)) {
                    tables.putIfAbsent(table, findTablePrimaryKeys(md, catalog, schema, table));
                }
                name = metaData.getColumnName(i);
                label = metaData.getColumnLabel(i);
                typeName = metaData.getColumnTypeName(i);
                columnType = metaData.getColumnType(i);
                pk = isPk(tables, table, name);

                Field field = new Field(label, typeName, columnType, pk);

                // 尝试从 DatabaseMetaData 获取自增信息
                // 注意：DQL 模式下，表名可能不准确，所以这里使用 try-catch 处理可能的异常
                try (ResultSet columnMetadata = md.getColumns(catalog, schema, table, name)) {
                    if (columnMetadata.next()) {
                        String isAutoIncrement = columnMetadata.getString("IS_AUTOINCREMENT");
                        if ("YES".equalsIgnoreCase(isAutoIncrement)) {
                            field.setAutoincrement(true);
                        } else {
                            field.setAutoincrement(false);
                        }
                    } else {
                        // 如果查询不到，默认为 false
                        field.setAutoincrement(false);
                    }
                } catch (SQLException e) {
                    // 如果获取失败（例如表名不准确），默认为 false
                    field.setAutoincrement(false);
                }

                fields.add(field);
            }
        } finally {
            tables.clear();
        }
        MetaInfo dqlMeta = new MetaInfo().setColumn(fields);
        dqlMeta.setName(tableName);
        return dqlMeta;
    }

    /**
     * 获取查询条件SQL
     *
     * @param commandConfig
     * @return
     */
    protected String getQueryFilterSql(CommandConfig commandConfig) {
        List<Filter> filter = commandConfig.getFilter();
        if (CollectionUtils.isEmpty(filter)) {
            return "";
        }
        Table table = commandConfig.getTable();
        Map<String, Field> fieldMap = new HashMap<>();
        table.getColumn().forEach(field -> fieldMap.put(field.nameIgnoreCase(), field));

        // 过滤条件SQL
        StringBuilder sql = new StringBuilder();

        // 拼接并且SQL
        String addSql = buildFilterSql(fieldMap, OperationEnum.AND.getName(), filter);
        // 如果Add条件存在
        if (StringUtil.isNotBlank(addSql)) {
            sql.append(addSql);
        }

        // 拼接或者SQL
        String orSql = buildFilterSql(fieldMap, OperationEnum.OR.getName(), filter);
        // 如果Or条件和Add条件都存在
        if (StringUtil.isNotBlank(orSql) && StringUtil.isNotBlank(addSql)) {
            sql.append(" OR ");
        }
        sql.append(orSql);

        // 自定义SQL
        Optional<Filter> sqlFilter = filter.stream()
                .filter(f -> StringUtil.equals(f.getOperation(), OperationEnum.SQL.getName())).findFirst();
        sqlFilter.ifPresent(f -> sql.append(f.getValue()));

        return sql.toString();
    }

    /**
     * 去重列名
     *
     * @param column
     * @return
     */
    private List<Field> filterColumn(List<Field> column) {
        Set<String> mark = new HashSet<>();
        List<Field> fields = new ArrayList<>();
        for (Field c : column) {
            String name = c.getName();
            if (StringUtil.isBlank(name)) {
                throw new SdkException("The field name can not be empty.");
            }
            if (!mark.contains(name)) {
                fields.add(c);
                mark.add(name);
            }
        }
        return fields;
    }

    /**
     * 解析有效的 catalog 和 schema
     * 子类必须实现此方法以提供特定数据库的实现
     *
     * @param conn    数据库连接
     * @param catalog 传入的 catalog（可能为 null）
     * @param schema  传入的 schema（可能为 null）
     * @return CatalogAndSchema 包含有效的 catalog 和 schema
     */
    protected abstract CatalogAndSchema resolveEffectiveCatalogAndSchema(Connection conn, String catalog, String schema)
            throws SQLException;

    /**
     * Catalog 和 Schema 的封装类
     */
    protected static class CatalogAndSchema {
        private final String catalog;
        private final String schema;

        public CatalogAndSchema(String catalog, String schema) {
            this.catalog = catalog;
            this.schema = schema;
        }

        public String getCatalog() {
            return catalog;
        }

        public String getSchema() {
            return schema;
        }
    }

    /**
     * 获取表列表
     *
     * @param connectorInstance
     * @param catalog
     * @param schema
     * @param tableNamePattern
     * @return
     */
    private List<Table> getTable(DatabaseConnectorInstance connectorInstance, String catalog, String schema,
            String tableNamePattern) throws Exception {
        return connectorInstance.execute(databaseTemplate -> {
            List<Table> tables = new ArrayList<>();
            SimpleConnection connection = databaseTemplate.getSimpleConnection();
            Connection conn = connection.getConnection();

            // 使用子类可重写的方法解析有效的 catalog 和 schema
            CatalogAndSchema catalogAndSchema = resolveEffectiveCatalogAndSchema(conn, catalog, schema);
            String effectiveCatalog = catalogAndSchema.getCatalog();
            String effectiveSchema = catalogAndSchema.getSchema();

            String[] types = { TableTypeEnum.TABLE.getCode(), TableTypeEnum.VIEW.getCode(),
                    TableTypeEnum.MATERIALIZED_VIEW.getCode() };
            try (ResultSet rs = conn.getMetaData().getTables(effectiveCatalog, effectiveSchema, tableNamePattern,
                    types)) {
                while (rs.next()) {
                    final String tableName = rs.getString("TABLE_NAME");
                    final String tableType = rs.getString("TABLE_TYPE");
                    tables.add(new Table(tableName, tableType, null, null, null));
                }
            }
            return tables;
        });
    }

    /**
     * 根据过滤条件获取查询SQL
     *
     * @param fieldMap
     * @param operator {@link OperationEnum}
     * @param filter
     * @return
     */
    private String buildFilterSql(Map<String, Field> fieldMap, String operator, List<Filter> filter) {
        List<Filter> list = filter.stream().filter(f -> StringUtil.equals(f.getOperation(), operator))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(list)) {
            return "";
        }

        int size = list.size();
        int end = size - 1;
        StringBuilder sql = new StringBuilder();
        sql.append("(");
        Filter c = null;
        for (int i = 0; i < size; i++) {
            c = list.get(i);
            Field field = fieldMap.get(c.getName() == null ? null : c.getName().toLowerCase());
            Assert.notNull(field, "条件字段无效.");
            // 使用模板系统的统一方法构建带引号的字段名
            sql.append(sqlTemplate.buildColumn(buildFieldName(field)));
            // 如果使用了函数则不加引号
            FilterEnum filterEnum = FilterEnum.getFilterEnum(c.getFilter());
            switch (filterEnum) {
                case IN:
                    sql.append(StringUtil.SPACE).append(FilterEnum.IN.getName()).append(StringUtil.SPACE).append("(");
                    sql.append(c.getValue());
                    sql.append(")");
                    break;
                case IS_NULL:
                case IS_NOT_NULL:
                    sql.append(StringUtil.SPACE).append(filterEnum.getName().toUpperCase()).append(StringUtil.SPACE);
                    break;
                default:
                    // > 10
                    sql.append(StringUtil.SPACE).append(c.getFilter()).append(StringUtil.SPACE);
                    sql.append(buildFilterValue(c.getValue()));
                    break;
            }
            if (i < end) {
                sql.append(StringUtil.SPACE).append(operator).append(StringUtil.SPACE);
            }
        }
        sql.append(")");
        return sql.toString();
    }

    /**
     * 获取条件值引号（如包含系统表达式则排除引号）
     *
     * @param value
     * @return
     */
    private String buildFilterValue(String value) {
        if (StringUtil.isNotBlank(value)) {
            // 排除定时表达式
            if (QuartzFilterEnum.getQuartzFilterEnum(value) == null) {
                // 系统函数表达式 $select max(update_time)$
                Matcher matcher = Pattern.compile("^[$].*[$]$").matcher(value);
                if (matcher.find()) {
                    return StringUtil.substring(value, 1, value.length() - 1);
                }
            }
        }
        return StringUtil.SINGLE_QUOTATION + value + StringUtil.SINGLE_QUOTATION;
    }

    /**
     * 返回表主键
     *
     * @param md
     * @param catalog
     * @param schema
     * @param tableName
     * @return
     * @throws SQLException
     */
    private List<String> findTablePrimaryKeys(DatabaseMetaData md, String catalog, String schema, String tableName)
            throws SQLException {
        // 根据表名获得主键结果集
        // 注意：必须按 KEY_SEQ 排序以保证复合主键的列顺序与定义一致
        ResultSet rs = null;
        List<String> primaryKeys = new ArrayList<>();
        try {
            rs = md.getPrimaryKeys(catalog, schema, tableName);
            // 收集 (KEY_SEQ, COLUMN_NAME) 对，按 KEY_SEQ 排序
            List<KeySeqName> entries = new ArrayList<>();
            while (rs.next()) {
                int keySeq = rs.getInt("KEY_SEQ");
                String columnName = rs.getString("COLUMN_NAME");
                entries.add(new KeySeqName(keySeq, columnName));
            }
            entries.sort(Comparator.comparingInt(e -> e.keySeq));
            for (KeySeqName entry : entries) {
                primaryKeys.add(entry.name);
            }
        } finally {
            DatabaseUtil.close(rs);
        }
        return primaryKeys;
    }

    /** 临时内部类：存储主键列的 KEY_SEQ 和列名 */
    private static class KeySeqName {
        final int keySeq;
        final String name;

        KeySeqName(int keySeq, String name) {
            this.keySeq = keySeq;
            this.name = name;
        }
    }

    private List<Object[]> batchRows(List<Field> fields, List<Map> data) {
        return data.stream().map(row -> batchRow(fields, row)).collect(Collectors.toList());
    }

    // 加工用于填充 sql 的参数的变量列表。
    private Object[] batchRow(List<Field> fields, Map row) {
        final int size = fields.size();
        Object[] args = new Object[size];
        for (int i = 0; i < size; i++) {
            args[i] = row.get(fields.get(i).getName());
        }
        return args;
    }

    private boolean isPk(Map<String, List<String>> tables, String tableName, String name) {
        List<String> pk = tables.get(tableName);
        if (CollectionUtils.isEmpty(pk)) {
            return false;
        }
        return pk.stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    @Override
    public Result writerDDL(DatabaseConnectorInstance connectorInstance, DDLConfig config,
            org.dbsyncer.sdk.plugin.PluginContext context) throws Exception {
        Assert.hasText(config.getSql(), "执行SQL语句不能为空.");
        logger.info("执行 DDL SQL: {}", config.getSql());
        connectorInstance.execute(databaseTemplate -> {
            String sql = config.getSql();
            databaseTemplate.execute("/*dbs*/".concat(sql));
            return true;
        });
        logger.info("DDL SQL 执行成功: {}", config.getSql());

        Result result = new Result();
        Map<String, String> successMap = new HashMap<>();
        successMap.put(ConfigConstant.BINLOG_DATA, config.getSql());
        result.addSuccessData(Collections.singletonList(successMap));
        return result;
    }

    @Override
    public SourceToIRConverter getSourceToIRConverter() {
        return this.sourceToIRConverter;
    }

    @Override
    public IRToTargetConverter getIRToTargetConverter() {
        return this.irToTargetConverter;
    }

    @Override
    public SqlTemplate getSqlTemplate() {
        return sqlTemplate;
    }

    /**
     * 是否支持字段差异检测
     * 子类可重写此方法声明自己的能力
     * 默认不支持，MySQL 和 SQL Server 连接器重写返回 true
     *
     * @return true 表示支持字段差异检测，false 表示不支持
     */
    public boolean supportsFieldDifference() {
        return false;
    }
}
