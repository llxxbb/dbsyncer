/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql;

import org.dbsyncer.connector.mysql.cdc.MySQLListener;
import org.dbsyncer.connector.mysql.converter.IRToMySQLConverter;
import org.dbsyncer.connector.mysql.converter.MySQLToIRConverter;
import org.dbsyncer.connector.mysql.schema.MySQLSyncConverter;
import org.dbsyncer.connector.mysql.storage.MySQLStorageService;
import org.dbsyncer.connector.mysql.validator.MySQLConfigValidator;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.connector.database.sql.impl.MySQLTemplate;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.listener.DatabaseQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.schema.SyncConverter;
import org.dbsyncer.sdk.storage.StorageService;
import org.dbsyncer.common.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL连接器实现
 */
public class MySQLConnector extends AbstractDatabaseConnector {


    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final MySQLSyncConverter schemaResolver = new MySQLSyncConverter();


    public MySQLConnector() {
        sqlTemplate = new MySQLTemplate(schemaResolver);
        configValidator = new MySQLConfigValidator();
        sourceToIRConverter = new MySQLToIRConverter();
        irToTargetConverter = new IRToMySQLConverter(sqlTemplate);
    }

    @Override
    public String getConnectorType() {
        return "MySQL";
    }

    @Override
    public Listener getListener(String listenerType) {
        if (ListenerTypeEnum.isTiming(listenerType)) {
            return new DatabaseQuartzListener();
        }

        if (ListenerTypeEnum.isLog(listenerType)) {
            return new MySQLListener();
        }
        return null;
    }

    @Override
    public StorageService getStorageService() {
        return new MySQLStorageService();
    }

    // MySQL 8.4.0 版本编码（见 BinaryLogRemoteClient.parseVersion）
    // MySQL 8.4 起移除 SHOW MASTER STATUS，需改用 SHOW BINARY LOG STATUS
    private static final long MYSQL_VERSION_8_4 = 8400000000L;

    @Override
    public Map<String, String> getPosition(
            org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance connectorInstance) throws Exception {
        // 使用 JDBC 轻量查询获取当前位点（与增量同步的 binlog 协议互补）
        // 返回 {fileName, position, gtid}，其中 gtid 可能为空字符串（服务器不支持 GTID）
        Map<String, String> position = connectorInstance.execute(conn -> {
            Connection jdbcConn = conn.getConnection();
            long version = parseMysqlVersion(jdbcConn);
            String query = version >= MYSQL_VERSION_8_4 ? "SHOW BINARY LOG STATUS" : "SHOW MASTER STATUS";
            
            Map<String, Object> result = conn.queryForMap(query);
            if (result == null || result.isEmpty()) {
                throw new RuntimeException("获取MySQL当前binlog位置失败");
            }
            
            Map<String, String> pos = new HashMap<>();
            pos.put("fileName", (String) result.get("File"));
            pos.put("position", String.valueOf(result.get("Position")));
            // Executed_Gtid_Set 是第 5 列，MySQL 5.6.5+、8.4、MariaDB 10.0+ 一致
            Object gtidObj = result.get("Executed_Gtid_Set");
            pos.put("gtid", gtidObj != null ? gtidObj.toString().trim() : "");
            return pos;
        });
        return position;
    }

    /**
     * 解析 MySQL 版本号为可比较的数字编码
     */
    private long parseMysqlVersion(Connection conn) {
        try {
            String version = conn.getMetaData().getDatabaseProductVersion();
            if (version == null || version.trim().isEmpty()) {
                return 0;
            }
            // 截断后缀：5.7.44-log / 8.0.32+dfsg / 10.11.6-MariaDB-...
            String core = version.trim();
            for (String separator : new String[]{"-", "+", " "}) {
                int idx = core.indexOf(separator);
                if (idx >= 0) {
                    core = core.substring(0, idx);
                }
            }
            StringBuilder digits = new StringBuilder();
            for (String segment : core.split("[.]")) {
                for (int i = 0; i < segment.length(); i++) {
                    char c = segment.charAt(i);
                    if (c >= '0' && c <= '9') {
                        digits.append(c);
                    }
                }
            }
            if (digits.length() == 0) return 0;
            while (digits.length() < 10) digits.append('0');
            if (digits.length() > 10) digits.setLength(10);
            return Long.parseLong(digits.toString());
        } catch (Exception e) {
            logger.warn("Failed to parse MySQL version: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public Integer getStreamingFetchSize(ReaderContext context) {
        return Integer.MIN_VALUE; // MySQL流式处理特殊值
    }

    @Override
    public SyncConverter getSchemaResolver() {
        return schemaResolver;
    }

    @Override
    protected List<Field> enhanceFields(DatabaseConnectorInstance connectorInstance, List<Field> fields, String tableName) throws Exception {
        DatabaseConfig cfg = connectorInstance.getConfig();
        String schema = getSchema(cfg);

        connectorInstance.execute(databaseTemplate -> {
            Connection conn = databaseTemplate.getConnection();
            String catalog = conn.getCatalog();

            // 一次性查询 ENUM/SET 枚举值 + 字段字符集
            String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, CHARACTER_SET_NAME " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";

            List<Map<String, Object>> columns = databaseTemplate.queryForList(sql, catalog, tableName);
            if (columns != null && !columns.isEmpty()) {
                for (Map<String, Object> row : columns) {
                    String columnName = (String) row.get("COLUMN_NAME");
                    if (columnName == null) {
                        continue;
                    }
                    // 匹配 Field 对象
                    Field field = null;
                    for (Field f : fields) {
                        if (columnName.equals(f.getName())) {
                            field = f;
                            break;
                        }
                    }
                    if (field == null) {
                        continue;
                    }

                    // ENUM/SET 枚举值
                    String dataType = (String) row.get("DATA_TYPE");
                    if ("enum".equals(dataType) || "set".equals(dataType)) {
                        String columnType = (String) row.get("COLUMN_TYPE");
                        if (columnType != null) {
                            List<String> values = parseEnumValues(columnType);
                            if (values != null && !values.isEmpty()) {
                                field.setEnumValues(values);
                            }
                        }
                    }

                    // 字段字符集
                    if (field.getCharset() == null) {
                        String charsetName = (String) row.get("CHARACTER_SET_NAME");
                        if (charsetName != null) {
                            field.setCharset(charsetName);
                        }
                    }
                }
            }
            return null;
        });

        return fields;
    }

    /**
     * 解析 MySQL COLUMN_TYPE 字符串中的枚举值列表
     * 例如：enum('a','b','c') -> ["a", "b", "c"]
     *       set('x','y','z') -> ["x", "y", "z"]
     *
     * @param columnType COLUMN_TYPE 字符串，如 "enum('a','b','c')"
     * @return 枚举值列表，如果解析失败则返回 null
     */
    private List<String> parseEnumValues(String columnType) {
        if (columnType == null || columnType.trim().isEmpty()) {
            return null;
        }

        int startIdx = columnType.indexOf('(');
        int endIdx = columnType.lastIndexOf(')');
        if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx) {
            return null;
        }

        String valuesStr = columnType.substring(startIdx + 1, endIdx);
        List<String> values = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < valuesStr.length(); i++) {
            char c = valuesStr.charAt(i);

            if (c == '\'' || c == '"') {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (c == quoteChar) {
                    inQuotes = false;
                    quoteChar = 0;
                } else {
                    currentValue.append(c);
                }
            } else if (c == ',' && !inQuotes) {
                String value = currentValue.toString().trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
                currentValue.setLength(0);
            } else if (!inQuotes && Character.isWhitespace(c)) {
                continue;
            } else {
                currentValue.append(c);
            }
        }

        String lastValue = currentValue.toString().trim();
        if (!lastValue.isEmpty()) {
            values.add(lastValue);
        }

        return values.isEmpty() ? null : values;
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
            throw new UnsupportedOperationException("MySQL 连接器不支持自动生成 CREATE TABLE DDL");
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
        return sqlTemplate.buildCreateTableSql(null, targetTableName, fields, primaryKeysList);
    }

    @Override
    protected CatalogAndSchema resolveEffectiveCatalogAndSchema(Connection conn, String catalog, String schema) throws SQLException {
        // MySQL: schema=null, catalog=database name
        String effectiveCatalog = (catalog != null) ? catalog : conn.getCatalog();
        return new CatalogAndSchema(effectiveCatalog, null);
    }

    @Override
    public boolean supportsFieldDifference() {
        return true;
    }
}