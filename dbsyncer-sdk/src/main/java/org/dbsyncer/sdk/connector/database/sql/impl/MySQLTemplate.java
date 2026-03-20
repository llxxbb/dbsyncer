package org.dbsyncer.sdk.connector.database.sql.impl;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.SchemaResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL特定SQL模板实现
 */
public class MySQLTemplate extends AbstractSqlTemplate {

    public MySQLTemplate(SchemaResolver schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public String getLeftQuotation() {
        return "`";
    }

    @Override
    public String getRightQuotation() {
        return "`";
    }

    @Override
    public String buildUpsertSql(String schemaTable, List<Field> fields, List<String> primaryKeys) {
        String fieldNames = fields.stream()
                .map(field -> buildColumn(field.getName()))
                .collect(java.util.stream.Collectors.joining(", "));
        String placeholders = fields.stream()
                .map(field -> "?")
                .collect(java.util.stream.Collectors.joining(", "));
        String updateClause = fields.stream()
                .filter(field -> !field.isPk())
                .map(field -> buildColumn(field.getName()) + " = VALUES(" + buildColumn(field.getName()) + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        
        // 如果所有字段都是主键，updateClause 为空，需要至少添加一个更新表达式以满足 MySQL 语法要求
        // 使用第一个主键字段的虚拟更新（不会实际改变值）
        if (updateClause.isEmpty() && !fields.isEmpty()) {
            Field firstField = fields.get(0);
            updateClause = buildColumn(firstField.getName()) + " = " + buildColumn(firstField.getName());
        }
        
        return String.format("INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
                schemaTable, fieldNames, placeholders, updateClause);
    }

    @Override
    public String buildAddColumnSql(String tableName, Field field) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(buildQuotedTableName(tableName))
           .append(" ADD COLUMN ").append(buildColumn(field.getName()));
        
        // 转换类型并获取 MySQL 类型字符串
        String databaseType = convertToDatabaseType(field);
        sql.append(" ").append(databaseType);
        
        // 添加 NOT NULL 约束
        if (field.getNullable() != null && !field.getNullable()) {
            sql.append(" NOT NULL");
            // MySQL 语法要求：向非空表添加 NOT NULL 列时，必须提供 DEFAULT 值
            // 注意：这是为了满足 MySQL 的语法约束，不是通用的缺省值处理
            // 生成的 DEFAULT 值仅用于满足语法要求，不会影响数据同步结果
            // 使用转换后的 MySQL 类型名称来判断默认值
            String defaultValue = getDefaultValueForNotNullColumnByTypeName(databaseType);
            if (defaultValue != null) {
                sql.append(" DEFAULT ").append(defaultValue);
            }
        }
        
        // 添加 COMMENT
        if (field.getComment() != null && !field.getComment().isEmpty()) {
            sql.append(" COMMENT '").append(escapeMySQLString(field.getComment())).append("'");
        }
        
        return sql.toString();
    }

    @Override
    public String buildModifyColumnSql(String tableName, Field field) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(buildQuotedTableName(tableName))
           .append(" MODIFY COLUMN ").append(buildColumn(field.getName()))
           .append(" ").append(convertToDatabaseType(field));
        
        // 处理 NULL/NOT NULL 约束
        // 在 MySQL 中，要移除 NOT NULL 约束，需要显式指定 NULL
        if (field.getNullable() != null) {
            if (field.getNullable()) {
                // 字段可空：显式指定 NULL，以移除 NOT NULL 约束
                sql.append(" NULL");
            } else {
                // 字段不可空：添加 NOT NULL 约束
                sql.append(" NOT NULL");
            }
        }
        
        // 注意：不再支持 DEFAULT 值，因为数据同步不需要默认值支持
        
        // 添加 COMMENT
        if (field.getComment() != null && !field.getComment().isEmpty()) {
            sql.append(" COMMENT '").append(escapeMySQLString(field.getComment())).append("'");
        }
        
        return sql.toString();
    }

    @Override
    public String buildRenameColumnSql(String tableName, String oldFieldName, Field newField) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(buildQuotedTableName(tableName))
           .append(" CHANGE COLUMN ").append(buildColumn(oldFieldName))
           .append(" ").append(buildColumn(newField.getName()))
           .append(" ").append(convertToDatabaseType(newField));
        
        // 添加 NOT NULL 约束
        if (newField.getNullable() != null && !newField.getNullable()) {
            sql.append(" NOT NULL");
        }
        
        // 注意：不再支持 DEFAULT 值，因为数据同步不需要默认值支持
        
        // 添加 COMMENT
        if (newField.getComment() != null && !newField.getComment().isEmpty()) {
            sql.append(" COMMENT '").append(escapeMySQLString(newField.getComment())).append("'");
        }
        
        return sql.toString();
    }

    @Override
    public String buildDropColumnSql(String tableName, String fieldName) {
        return String.format("ALTER TABLE %s DROP COLUMN %s",
                buildQuotedTableName(tableName),
                buildColumn(fieldName));
    }

    @Override
    public String buildCreateTableSql(String schema, String tableName, List<Field> fields, List<String> primaryKeys) {
        List<String> columnDefs = new ArrayList<>();
        List<String> effectivePrimaryKeys = primaryKeys != null ? new ArrayList<>(primaryKeys) : new ArrayList<>();
        
        // MySQL 要求：AUTO_INCREMENT 列必须是主键
        // 检查是否有 AUTO_INCREMENT 字段，如果不在主键列表中，自动添加到主键列表
        for (Field field : fields) {
            if (field.isAutoincrement()) {
                String fieldName = field.getName();
                if (!effectivePrimaryKeys.contains(fieldName)) {
                    // AUTO_INCREMENT 字段不在主键列表中，添加到主键列表（放在最前面）
                    effectivePrimaryKeys.add(0, fieldName);
                }
            }
        }
        
        for (Field field : fields) {
            String ddlType = convertToDatabaseType(field);
            String columnName = buildColumn(field.getName());
            
            // 构建列定义：列名 类型 [NOT NULL] [AUTO_INCREMENT] [COMMENT 'comment']
            // 注意：不再支持 DEFAULT 值，因为数据同步不需要默认值支持
            StringBuilder columnDef = new StringBuilder();
            columnDef.append(String.format("  %s %s", columnName, ddlType));
            
            if (field.getNullable() != null && !field.getNullable()) {
                columnDef.append(" NOT NULL");
            }
            
            if (field.isAutoincrement()) {
                columnDef.append(" AUTO_INCREMENT");
            }
            
            if (field.getComment() != null && !field.getComment().isEmpty()) {
                String escapedComment = escapeMySQLString(field.getComment());
                columnDef.append(String.format(" COMMENT '%s'", escapedComment));
            }
            
            columnDefs.add(columnDef.toString());
        }
        
        // 构建主键定义
        String pkClause = "";
        if (!effectivePrimaryKeys.isEmpty()) {
            String pkColumns = effectivePrimaryKeys.stream()
                    .map(this::buildColumn)
                    .collect(java.util.stream.Collectors.joining(", "));
            pkClause = String.format(",\n  PRIMARY KEY (%s)", pkColumns);
        }
        
        // 组装完整的 CREATE TABLE 语句
        String columns = String.join(",\n", columnDefs);
        return String.format("CREATE TABLE %s (\n%s%s\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                buildTable(schema, tableName), columns, pkClause);
    }


    @Override
    public String convertToDatabaseType(Field column) {
        // 使用 SchemaResolver 进行类型转换，完全委托给 SchemaResolver 处理
        Field targetField = schemaResolver.fromStandardType(column);
        String typeName = targetField.getTypeName();
        
        // 处理参数（长度、精度等）
        switch (typeName) {
            case "CHAR":
                if (column.getColumnSize() > 0) {
                    return typeName + "(" + column.getColumnSize() + ")";
                }
                throw new IllegalArgumentException("should give size for column: " + column.getTypeName());
            case "VARCHAR":
                if (column.getColumnSize() > 0) {
                    // MySQL VARCHAR 最大长度限制：对于 utf8mb4 字符集，最大字符数约为 16383
                    // 如果 columnSize 超过限制，应该使用 TEXT 类型
                    long columnSize = column.getColumnSize();
                    if (columnSize > 16383) {
                        // 超过 VARCHAR 限制，转换为 TEXT 类型
                        // 根据 columnSize 判断使用哪种 TEXT 类型
                        if (columnSize <= 65535L) {
                            return "TEXT";
                        } else if (columnSize <= 16777215L) {
                            return "MEDIUMTEXT";
                        } else {
                            return "LONGTEXT";
                        }
                    }
                    return typeName + "(" + column.getColumnSize() + ")";
                }
                throw new IllegalArgumentException("should give size for column: " + column.getTypeName());
            case "TEXT":
                // 根据 columnSize 判断使用哪种 TEXT 类型
                // MySQL 的 TEXT 类型容量：
                // - TINYTEXT: 最大 255 字节
                // - TEXT: 最大 65,535 字节 (64KB)
                // - MEDIUMTEXT: 最大 16,777,215 字节 (16MB)
                // - LONGTEXT: 最大 4,294,967,295 字节 (4GB)
                long columnSize = column.getColumnSize();
                if (columnSize > 0) {
                    if (columnSize <= 255L) {
                        return "TINYTEXT";
                    } else if (columnSize <= 65535L) {
                        return "TEXT";
                    } else if (columnSize <= 16777215L) {
                        return "MEDIUMTEXT";
                    } else {
                        return "LONGTEXT";
                    }
                }
                // 如果没有 columnSize 信息，默认使用 TEXT
                return "TEXT";
            case "DECIMAL":
                if (column.getColumnSize() > 0 && column.getRatio() >= 0) {
                    return typeName + "(" + column.getColumnSize() + "," + column.getRatio() + ")";
                } else if (column.getColumnSize() > 0) {
                    return typeName + "(" + column.getColumnSize() + ")";
                }
                return "DECIMAL(10,0)";
            case "VARBINARY":
                // BYTES 类型：用于小容量二进制数据
                // 对于固定长度的二进制数据，使用 BINARY 类型
                // MySQL 的 BINARY 类型用于固定长度二进制数据，VARBINARY 用于可变长度
                // 当 columnSize 存在且<=255 时，使用 BINARY 以获得更好的性能
                long binarySize = column.getColumnSize();
                if (binarySize > 0 && binarySize <= 255) {
                    // 固定长度且小于等于 255，使用 BINARY
                    return "BINARY(" + binarySize + ")";
                } else if (binarySize > 255 && binarySize <= 65535) {
                    // 有长度但大于 255 且<=65535，使用 VARBINARY
                    return "VARBINARY(" + binarySize + ")";
                }
                // 没有长度信息或长度超过 65535，默认使用 VARBINARY(65535)
                return "VARBINARY(65535)";
            case "BLOB":
                // BLOB 类型：用于大容量二进制数据
                // MySQL 的 BLOB 类型容量：
                // - TINYBLOB: 最大 255 字节
                // - BLOB: 最大 65,535 字节 (64KB)
                // - MEDIUMBLOB: 最大 16,777,215 字节 (16MB)
                // - LONGBLOB: 最大 4,294,967,295 字节 (4GB)
                long blobSize = column.getColumnSize();
                if (blobSize > 0) {
                    if (blobSize <= 255L) {
                        return "TINYBLOB";
                    } else if (blobSize <= 65535L) {
                        return "BLOB";
                    } else if (blobSize <= 16777215L) {
                        return "MEDIUMBLOB";
                    } else {
                        return "LONGBLOB";
                    }
                }
                // 没有长度信息，默认使用 BLOB（适合大多数场景）
                return "BLOB";
            case "BINARY":
                // 如果已经是 BINARY 类型，保持 BINARY 并添加长度
                if (column.getColumnSize() > 0) {
                    return "BINARY(" + column.getColumnSize() + ")";
                }
                return "BINARY";
            case "TINYINT":
                // BOOLEAN 类型映射到 TINYINT，对于布尔类型使用 TINYINT(1)
                // 检查是否是 BOOLEAN 标准类型（通过 column 的 typeName 判断）
                String standardType = column.getTypeName();
                if ("BOOLEAN".equals(standardType)) {
                    return "TINYINT(1)";
                }
                // 如果是普通的 TINYINT 类型，根据 columnSize 处理
                if (column.getColumnSize() > 0) {
                    return typeName + "(" + column.getColumnSize() + ")";
                }
                return typeName;
            case "ENUM":
                // ENUM 类型必须有枚举值列表
                List<String> enumValues = column.getEnumValues();
                if (enumValues != null && !enumValues.isEmpty()) {
                    String valuesStr = enumValues.stream()
                            .map(v -> "'" + v.replace("'", "''") + "'")
                            .collect(java.util.stream.Collectors.joining(", "));
                    return "ENUM(" + valuesStr + ")";
                }
                // 如果没有枚举值，回退到默认行为（但这会导致 SQL 错误，所以抛异常提示）
                throw new IllegalArgumentException("ENUM type must have enum values for column: " + column.getName());
            case "SET":
                // SET 类型必须有集合值列表
                List<String> setValues = column.getEnumValues();
                if (setValues != null && !setValues.isEmpty()) {
                    String valuesStr = setValues.stream()
                            .map(v -> "'" + v.replace("'", "''") + "'")
                            .collect(java.util.stream.Collectors.joining(", "));
                    return "SET(" + valuesStr + ")";
                }
                // 如果没有集合值，回退到默认行为（但这会导致 SQL 错误，所以抛异常提示）
                throw new IllegalArgumentException("SET type must have set values for column: " + column.getName());
            default:
                return typeName;
        }
    }

    /**
     * 根据 MySQL 数据库类型名称获取 NOT NULL 列的默认值
     * 
     * 注意：此方法仅用于满足 MySQL 的语法约束，不是通用的缺省值处理。
     * MySQL 要求：向非空表添加 NOT NULL 列时，必须提供 DEFAULT 值。
     * 
     * 背景说明：
     * - 项目在 2.7.0 版本取消了通用的缺省值处理（见 release-log.md），因为各数据库缺省值函数表达差异很大
     * - 但 MySQL 的语法要求必须提供 DEFAULT 值，否则 DDL 执行会失败
     * - 此方法生成的 DEFAULT 值仅用于满足语法要求，不会影响数据同步结果（数据同步不依赖缺省值）
     * 
     * @param typeName MySQL 数据库类型名称（如 "BIGINT", "VARCHAR(50)" 等）
     * @return 默认值表达式，如果不支持则返回 null
     */
    public static String getDefaultValueForNotNullColumnByTypeName(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return null;
        }
        
        String upperTypeName = typeName.toUpperCase();
        
        // 字符串类型：使用空字符串
        if (upperTypeName.contains("VARCHAR") || upperTypeName.contains("CHAR") || 
            upperTypeName.contains("TEXT")) {
            return "''";
        }
        
        // 数值类型：使用 0
        if (upperTypeName.contains("INT") || upperTypeName.contains("BIGINT") || 
            upperTypeName.contains("SMALLINT") || upperTypeName.contains("TINYINT") ||
            upperTypeName.contains("DECIMAL") || upperTypeName.contains("NUMERIC") ||
            upperTypeName.contains("FLOAT") || upperTypeName.contains("DOUBLE") ||
            upperTypeName.contains("REAL")) {
            return "0";
        }
        
        // 布尔类型（TINYINT(1)）：使用 0
        if (upperTypeName.equals("BOOLEAN") || upperTypeName.equals("BOOL")) {
            return "0";
        }
        
        // 日期时间类型：使用 '1900-01-01' 或 '1900-01-01 00:00:00'
        if (upperTypeName.contains("DATE") || upperTypeName.contains("TIME")) {
            if (upperTypeName.contains("DATETIME") || upperTypeName.contains("TIMESTAMP")) {
                return "'1900-01-01 00:00:00'";
            }
            return "'1900-01-01'";
        }
        
        // 二进制类型：使用 0x（空二进制）
        if (upperTypeName.contains("BINARY") || upperTypeName.contains("VARBINARY") ||
            upperTypeName.contains("BLOB")) {
            return "0x";
        }
        
        // 其他类型：返回 null，让调用者决定如何处理
        return null;
    }

    /**
     * 转义 MySQL 字符串字面量中的特殊字符
     * 在 MySQL 中，字符串字面量需要转义以下字符：
     * - 单引号 (') -> ''
     * - 反斜杠 (\) -> \\
     * - 其他控制字符也需要转义
     * 
     * @param str 原始字符串
     * @return 转义后的字符串
     */
    private String escapeMySQLString(String str) {
        if (str == null) {
            return null;
        }
        // 先转义反斜杠，再转义单引号
        // 注意：必须先转义反斜杠，否则转义单引号时可能会影响反斜杠的转义
        return str.replace("\\", "\\\\").replace("'", "''");
    }

    @Override
    public String buildMetadataCountSql(String schema, String tableName) {
        // 转义单引号防止SQL注入
        String escapedTableName = tableName.replace("'", "''");
        
        if (schema != null && !schema.trim().isEmpty()) {
            String escapedSchema = schema.replace("'", "''");
            return String.format(
                "SELECT table_rows FROM information_schema.tables WHERE table_schema = '%s' AND table_name = '%s'",
                escapedSchema,
                escapedTableName
            );
        } else {
            // 使用 DATABASE() 函数获取当前数据库名
            return String.format(
                "SELECT table_rows FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '%s'",
                escapedTableName
            );
        }
    }

    @Override
    public String buildAlterPrimaryKeySql(String tableName, List<String> oldPrimaryKeys, List<String> newPrimaryKeys, String schema) {
        String quotedTableName = buildTable(schema, tableName);
        String quotedKeys = buildQuotedFieldList(newPrimaryKeys);
        
        if (oldPrimaryKeys != null && !oldPrimaryKeys.isEmpty()) {
            return "ALTER TABLE " + quotedTableName + " DROP PRIMARY KEY, ADD PRIMARY KEY (" + quotedKeys + ")";
        } else {
            return "ALTER TABLE " + quotedTableName + " ADD PRIMARY KEY (" + quotedKeys + ")";
        }
    }
}