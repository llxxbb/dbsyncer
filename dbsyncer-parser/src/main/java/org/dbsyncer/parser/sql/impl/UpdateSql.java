/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.sql.impl;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.model.FieldMapping;
import org.dbsyncer.parser.sql.SqlParser;

import java.util.List;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2023-12-10 00:45
 */
public final class UpdateSql implements SqlParser {

    private String sql;

    private String sourceTableName;

    private String targetTableName;

    private List<FieldMapping> fieldMappingList;

    private List<Object> beforeRow;

    private List<String> primaryKeys;

    public UpdateSql(String sql, String sourceTableName, String targetTableName, List<FieldMapping> fieldMappingList) {
        this.sql = sql;
        this.sourceTableName = sourceTableName;
        this.targetTableName = targetTableName;
        this.fieldMappingList = fieldMappingList;
    }

    public UpdateSql(String sql, String sourceTableName, String targetTableName, List<FieldMapping> fieldMappingList, 
                     List<Object> beforeRow, List<String> primaryKeys) {
        this.sql = sql;
        this.sourceTableName = sourceTableName;
        this.targetTableName = targetTableName;
        this.fieldMappingList = fieldMappingList;
        this.beforeRow = beforeRow;
        this.primaryKeys = primaryKeys;
    }

    @Override
    public String parse() {
        try {
            Update update = (Update) CCJSqlParserUtil.parse(sql);
            Table table = new Table(targetTableName);
            update.setTable(table);
            for (UpdateSet updateSet : update.getUpdateSets()) {
                List<Column> columns = updateSet.getColumns();
                for (Column column : columns) {
                    String colName = column.getColumnName().replaceAll("\"", "");
                    fieldMappingList.stream()
                            .filter(x -> x.matchesSource(colName))
                            .findFirst().ifPresent(
                            fieldMapping -> column.setColumnName(fieldMapping.getTarget()));
                }
            }
            
            if (primaryKeys != null && !primaryKeys.isEmpty() && beforeRow != null) {
                whereParseForPrimaryKeyUpdate((BinaryExpression) update.getWhere());
            } else {
                whereParse(update.getWhere());
            }
            return update.toString();
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    private void whereParse(Expression expression) {
        BinaryExpression binaryExpression = (BinaryExpression) expression;
        Expression left = binaryExpression.getLeftExpression();
        Expression right = binaryExpression.getRightExpression();
        findColumn((BinaryExpression) left);
        findColumn((BinaryExpression) right);
    }

    private void whereParseForPrimaryKeyUpdate(BinaryExpression whereExpression) {
        if (whereExpression == null || primaryKeys == null || beforeRow == null) {
            return;
        }
        
        if (whereExpression.getLeftExpression() instanceof Column) {
            Column column = (Column) whereExpression.getLeftExpression();
            String colName = column.getColumnName().replaceAll("\"", "");
            
            for (int i = 0; i < primaryKeys.size(); i++) {
                if (StringUtil.equalsIgnoreCase(primaryKeys.get(i), colName)) {
                    whereExpression.setRightExpression(createValueExpression(beforeRow.get(i)));
                    return;
                }
            }
        }
        
        if (whereExpression.getLeftExpression() instanceof BinaryExpression) {
            findColumn((BinaryExpression) whereExpression.getLeftExpression());
        }
        if (whereExpression.getRightExpression() instanceof BinaryExpression) {
            findColumn((BinaryExpression) whereExpression.getRightExpression());
        }
    }


    private Expression createValueExpression(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return new net.sf.jsqlparser.expression.StringValue((String) value);
        }
        if (value instanceof Long || value instanceof Integer) {
            return new net.sf.jsqlparser.expression.LongValue(value.toString());
        }
        return new net.sf.jsqlparser.expression.LongValue(value.toString());
    }

    private void findColumn(BinaryExpression binaryExpression) {
        if (binaryExpression == null) {
            return;
        }
        if (binaryExpression.getLeftExpression() instanceof Column) {
            Column column = (Column) binaryExpression.getLeftExpression();
            String colName = column.getColumnName().replaceAll("\"", "");
            fieldMappingList.stream()
                    .filter(x -> x.matchesSource(colName))
                    .findFirst().ifPresent(
                    fieldMapping -> column.setColumnName(fieldMapping.getTarget()));
            return;
        }
        findColumn((BinaryExpression) binaryExpression.getLeftExpression());
        findColumn((BinaryExpression) binaryExpression.getRightExpression());
    }
}