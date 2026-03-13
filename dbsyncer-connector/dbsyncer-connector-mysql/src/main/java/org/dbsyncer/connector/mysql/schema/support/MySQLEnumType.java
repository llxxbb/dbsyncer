package org.dbsyncer.connector.mysql.schema.support;

import net.sf.jsqlparser.statement.create.table.ColDataType;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.EnumType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MySQLEnumType extends EnumType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("ENUM"));
    }

    @Override
    protected String merge(Object val, Field field) {
        if (val instanceof String) {
            return (String) val;
        }
        return throwUnsupportedException(val, field);
    }

    @Override
    public Field handleDDLParameters(ColDataType colDataType) {
        Field result = super.handleDDLParameters(colDataType);
        
        // 处理 ENUM 类型，保存枚举值列表并计算最大长度
        List<String> argsList = colDataType.getArgumentsStringList();
        if (argsList != null && !argsList.isEmpty()) {
            // 保存枚举值列表（清理引号）
            List<String> enumValues = argsList.stream()
                    .map(value -> {
                        String cleanValue = value.trim();
                        if ((cleanValue.startsWith("'") && cleanValue.endsWith("'")) ||
                            (cleanValue.startsWith("\"") && cleanValue.endsWith("\""))) {
                            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
                        }
                        return cleanValue;
                    })
                    .collect(java.util.stream.Collectors.toList());
            result.setEnumValues(enumValues);
            
            // 计算最大长度（ENUM 类型只存储单个值，取最长的那个）
            int maxLength = enumValues.stream()
                    .mapToInt(String::length)
                    .max()
                    .orElse(255);
            
            result.setColumnSize(maxLength);
        } else {
            // 如果没有参数，使用默认长度
            result.setColumnSize(255);
        }
        
        return result;
    }
}
