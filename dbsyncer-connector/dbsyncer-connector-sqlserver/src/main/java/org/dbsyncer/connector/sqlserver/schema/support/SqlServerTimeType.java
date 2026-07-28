package org.dbsyncer.connector.sqlserver.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.TimeType;

import java.sql.Time;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SqlServerTimeType extends TimeType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("TIME"));
    }

    @Override
    protected Time merge(Object val, Field field) {
        if (val instanceof Time) {
            return (Time) val;
        }
        if (val instanceof String) {
            try {
                return Time.valueOf((String) val);
            } catch (IllegalArgumentException e) {
                return throwUnsupportedException(val, field);
            }
        }
        return throwUnsupportedException(val, field);
    }
}
