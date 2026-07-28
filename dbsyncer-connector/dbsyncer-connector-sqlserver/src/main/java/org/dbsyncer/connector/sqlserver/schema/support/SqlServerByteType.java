package org.dbsyncer.connector.sqlserver.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.UnsignedByteType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SQL Server TINYINT 类型
 * 映射到标准类型 UNSIGNED_BYTE（SQL Server TINYINT 范围为 0~255，无符号）
 */
public final class SqlServerByteType extends UnsignedByteType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("TINYINT", "TINYINT IDENTITY"));
    }

    @Override
    protected Short merge(Object val, Field field) {
        if (val instanceof Number) {
            return (short) ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Short.parseShort((String) val);
            } catch (NumberFormatException e) {
                return throwUnsupportedException(val, field);
            }
        }
        return throwUnsupportedException(val, field);
    }
}
