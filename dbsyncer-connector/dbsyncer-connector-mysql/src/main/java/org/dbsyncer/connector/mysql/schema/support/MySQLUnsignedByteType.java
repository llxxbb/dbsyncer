package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.UnsignedByteType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MySQL 无符号字节类型支持
 */
public final class MySQLUnsignedByteType extends UnsignedByteType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("TINYINT UNSIGNED"));
    }

    @Override
    protected Short merge(Object val, Field field) {
        if (val instanceof Number) {
            return convertToUnsignedByte(((Number) val).intValue());
        }
        if (val instanceof String) {
            try {
                return convertToUnsignedByte(Integer.parseInt((String) val));
            } catch (NumberFormatException e) {
                return throwUnsupportedException(val, field);
            }
        }
        return throwUnsupportedException(val, field);
    }

    private Short convertToUnsignedByte(int intVal) {
        if (intVal < 0) {
            intVal = intVal & 0xFF;
        }
        return (short) Math.min(intVal, 255);
    }
}

