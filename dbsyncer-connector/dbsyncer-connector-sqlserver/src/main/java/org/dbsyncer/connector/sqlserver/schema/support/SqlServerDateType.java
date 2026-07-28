package org.dbsyncer.connector.sqlserver.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.DateType;

import java.sql.Date;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SqlServerDateType extends DateType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("DATE"));
    }

    @Override
    protected Date merge(Object val, Field field) {
        if (val instanceof Date) {
            return (Date) val;
        }
        if (val instanceof String) {
            try {
                return Date.valueOf((String) val);
            } catch (IllegalArgumentException e) {
                return throwUnsupportedException(val, field);
            }
        }
        return throwUnsupportedException(val, field);
    }
}
