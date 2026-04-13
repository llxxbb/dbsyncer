package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.UnsignedShortType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MySQL 无符号短整型支持
 */
public final class MySQLUnsignedShortType extends UnsignedShortType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("SMALLINT UNSIGNED"));
    }

    @Override
    protected Integer merge(Object val, Field field) {
        if (val instanceof Number) {
            return convertToUnsignedShort(((Number) val).intValue());
        }
        if (val instanceof String) {
            try {
                return convertToUnsignedShort(Integer.parseInt((String) val));
            } catch (NumberFormatException e) {
                return throwUnsupportedException(val, field);
            }
        }
        return throwUnsupportedException(val, field);
    }

    private Integer convertToUnsignedShort(int intVal) {
        if (intVal < 0) {
            intVal = intVal & 0xFFFF;
        }
        return Math.min(intVal, 65535);
    }
}

