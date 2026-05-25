package org.dbsyncer.connector.mysql.schema.support;

import net.sf.jsqlparser.statement.create.table.ColDataType;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.SetType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MySQL SET 类型支持
 */
public final class MySQLSetType extends SetType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("SET"));
    }

    @Override
    protected String merge(Object val, Field field) {
        if (val instanceof String) {
            return (String) val;
        }
        if (val instanceof Number) {
            return bitmaskToString(((Number) val).longValue(), field.getEnumValues());
        }
        return throwUnsupportedException(val, field);
    }

    @Override
    protected Object convert(Object val, Field field) {
        if (val instanceof String) {
            return val;
        }
        if (val instanceof Number) {
            return bitmaskToString(((Number) val).longValue(), field.getEnumValues());
        }
        return throwUnsupportedException(val, field);
    }

    /**
     * MySQL SET 底层是位图存储（bitmask），将位图值转换为逗号分隔的字符串。
     * 例如：bitmask=5 (二进制 101) → "val1,val3"
     */
    private String bitmaskToString(long bitmask, List<String> setValues) {
        if (setValues == null || setValues.isEmpty()) {
            return String.valueOf(bitmask);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < setValues.size(); i++) {
            if ((bitmask & (1L << i)) != 0) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(setValues.get(i));
            }
        }
        return sb.toString();
    }

    @Override
    public Field handleDDLParameters(ColDataType colDataType) {
        Field result = super.handleDDLParameters(colDataType);
        
        // 处理 SET 类型，保存集合值列表并计算最大长度
        List<String> argsList = colDataType.getArgumentsStringList();
        if (argsList != null && !argsList.isEmpty()) {
            // 保存集合值列表（清理引号）
            List<String> setValues = argsList.stream()
                    .map(value -> {
                        String cleanValue = value.trim();
                        if ((cleanValue.startsWith("'") && cleanValue.endsWith("'")) ||
                            (cleanValue.startsWith("\"") && cleanValue.endsWith("\""))) {
                            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
                        }
                        return cleanValue;
                    })
                    .collect(java.util.stream.Collectors.toList());
            result.setEnumValues(setValues);
            
            // 计算最大长度（SET 类型可以存储多个值的组合，逗号分隔）
            int totalLength = setValues.stream()
                    .mapToInt(String::length)
                    .sum();
            
            // 加上分隔符长度：如果有 N 个值，最多需要 N-1 个逗号
            int separatorLength = setValues.size() > 1 ? (setValues.size() - 1) : 0;
            int maxLength = totalLength + separatorLength;
            
            // 如果计算出的长度太大（超过 VARCHAR 最大限制），使用合理的上限
            if (maxLength > 65535) {
                maxLength = 65535;
            }
            
            result.setColumnSize(maxLength);
        } else {
            // 如果没有参数，使用默认长度
            result.setColumnSize(255);
        }
        
        return result;
    }
}
