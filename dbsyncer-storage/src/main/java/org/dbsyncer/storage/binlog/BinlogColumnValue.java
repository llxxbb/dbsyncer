/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.storage.binlog;

import com.google.protobuf.ByteString;
import org.dbsyncer.common.column.AbstractColumnValue;
import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.storage.enums.BinlogByteEnum;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2022-06-30 22:39
 */
public class BinlogColumnValue extends AbstractColumnValue<ByteString> {

    public BinlogColumnValue(ByteString v) {
        setValue(v);
    }

    @Override
    public String asString() {
        return getValue().toStringUtf8();
    }

    @Override
    public byte[] asByteArray() {
        return getValue().toByteArray();
    }

    @Override
    public Short asShort() {
        byte[] bytes = asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.SHORT.getByteLength());
        if (bytes.length == BinlogByteEnum.SHORT.getByteLength()) {
            buffer.put(bytes);
        } else {
            byte[] padded = new byte[BinlogByteEnum.SHORT.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 2 - bytes.length), Math.min(bytes.length, 2));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asShortBuffer().get();
    }

    @Override
    public Integer asInteger() {
        byte[] bytes = asByteArray();
        if (bytes.length == BinlogByteEnum.BYTE.getByteLength()) {
            return NumberUtil.toInt(asString());
        }
        if (bytes.length == BinlogByteEnum.SHORT.getByteLength()) {
            Short aShort = asShort();
            return new Integer(aShort);
        }

        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.INTEGER.getByteLength());
        if (bytes.length == BinlogByteEnum.INTEGER.getByteLength()) {
            buffer.put(bytes);
        } else {
            // 长度不匹配时，用零填充或截取到 4 字节
            byte[] padded = new byte[BinlogByteEnum.INTEGER.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 4 - bytes.length), Math.min(bytes.length, 4));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asIntBuffer().get();
    }

    @Override
    public Long asLong() {
        byte[] bytes = asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.LONG.getByteLength());
        if (bytes.length == BinlogByteEnum.LONG.getByteLength()) {
            buffer.put(bytes);
        } else {
            // 长度不匹配时，用零填充或截取到 8 字节
            byte[] padded = new byte[BinlogByteEnum.LONG.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 8 - bytes.length), Math.min(bytes.length, 8));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asLongBuffer().get();
    }

    @Override
    public Float asFloat() {
        byte[] bytes = asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.FLOAT.getByteLength());
        if (bytes.length == BinlogByteEnum.FLOAT.getByteLength()) {
            buffer.put(bytes);
        } else {
            byte[] padded = new byte[BinlogByteEnum.FLOAT.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 4 - bytes.length), Math.min(bytes.length, 4));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asFloatBuffer().get();
    }

    @Override
    public Double asDouble() {
        byte[] bytes = asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.DOUBLE.getByteLength());
        if (bytes.length == BinlogByteEnum.DOUBLE.getByteLength()) {
            buffer.put(bytes);
        } else {
            byte[] padded = new byte[BinlogByteEnum.DOUBLE.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 8 - bytes.length), Math.min(bytes.length, 8));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asDoubleBuffer().get();
    }

    @Override
    public Boolean asBoolean() {
        byte[] bytes = asByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(BinlogByteEnum.SHORT.getByteLength());
        if (bytes.length == BinlogByteEnum.SHORT.getByteLength()) {
            buffer.put(bytes);
        } else {
            byte[] padded = new byte[BinlogByteEnum.SHORT.getByteLength()];
            System.arraycopy(bytes, 0, padded, Math.max(0, 2 - bytes.length), Math.min(bytes.length, 2));
            buffer.put(padded);
        }
        buffer.flip();
        return buffer.asShortBuffer().get() == 1;
    }

    @Override
    public BigDecimal asBigDecimal() {
        return new BigDecimal(asString());
    }

    @Override
    public Date asDate() {
        return new Date(asLong());
    }

    @Override
    public Timestamp asTimestamp() {
        return new Timestamp(asLong());
    }

    @Override
    public Time asTime() {
        return new Time(asLong());
    }
}