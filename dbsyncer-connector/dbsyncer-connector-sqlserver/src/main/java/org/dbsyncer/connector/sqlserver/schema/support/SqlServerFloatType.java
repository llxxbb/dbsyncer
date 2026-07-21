package org.dbsyncer.connector.sqlserver.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.DoubleType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SQL Server FLOAT 类型支持
 * 专门处理 FLOAT 类型 (8 字节，双精度浮点数)
 * 返回 DataTypeEnum.FLOAT，与 DOUBLE (64 位 IEEE 754) 区分
 */
public final class SqlServerFloatType extends DoubleType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("FLOAT"));
    }

    @Override
    public DataTypeEnum getType() {
        return DataTypeEnum.FLOAT; // 明确返回 FLOAT 而非 DOUBLE
    }

    @Override
    protected Double merge(Object val, Field field) {
        if (val instanceof Double) {
            return (Double) val;
        }
        if (val instanceof Float) {
            return ((Float) val).doubleValue();
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    @Override
    protected Object convert(Object val, Field field) {
        if (val instanceof Double) {
            return val;
        }
        if (val instanceof Float) {
            return ((Float) val).doubleValue();
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return super.convert(val, field);
    }

}
