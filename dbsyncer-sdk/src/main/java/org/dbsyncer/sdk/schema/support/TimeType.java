package org.dbsyncer.sdk.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.AbstractDataType;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;

public abstract class TimeType extends AbstractDataType<Time> {

    protected TimeType() {
        super(Time.class);
    }

    @Override
    public DataTypeEnum getType() {
        return DataTypeEnum.TIME;
    }

    @Override
    protected Object convert(Object val, Field field) {
        if (val instanceof Time) {
            return val;
        }
        if (val instanceof Timestamp) {
            // 将 Timestamp 转换为 Time（只保留时间部分）
            return new Time(((Timestamp) val).getTime());
        }
        if (val instanceof Date) {
            // 将 Date 转换为 Time（只保留时间部分）
            return new Time(((Date) val).getTime());
        }
        return throwUnsupportedException(val, field);
    }
}