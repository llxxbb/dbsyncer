/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.TimeType;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2024-11-26 22:59
 */
public final class MySQLTimeType extends TimeType {

    @Override
    public Set<String> getSupportedTypeName() {
        return new HashSet<>(Arrays.asList("TIME"));
    }

    @Override
    protected Time merge(Object val, Field field) {
        if (val instanceof Time) {
            return (Time) val;
        }
        if (val instanceof Timestamp) {
            return new Time(((Timestamp) val).getTime());
        }
        if (val instanceof java.util.Date) {
            return new Time(((java.util.Date) val).getTime());
        }
        if (val instanceof LocalDateTime) {
            return Time.valueOf(((LocalDateTime) val).toLocalTime());
        }
        return throwUnsupportedException(val, field);
    }
}