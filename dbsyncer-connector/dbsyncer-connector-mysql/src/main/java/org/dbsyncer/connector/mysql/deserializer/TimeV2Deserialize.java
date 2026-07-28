/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.deserializer;

import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Time;
import java.util.Calendar;

public final class TimeV2Deserialize {

    public Serializable deserializeTimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
        long time = bigEndianLong(inputStream.read(3), 0, 3);
        int fsp = deserializeFractionalSeconds(meta, inputStream);

        int hour = bitSlice(time, 2, 10, 24);
        int minute = bitSlice(time, 12, 6, 24);
        int second = bitSlice(time, 18, 6, 24);
        int millis = fsp / 1000;

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(1970, Calendar.JANUARY, 1, hour, minute, second);
        cal.set(Calendar.MILLISECOND, millis);

        return new Time(cal.getTimeInMillis());
    }

    private int deserializeFractionalSeconds(int meta, ByteArrayInputStream inputStream) throws IOException {
        int length = (meta + 1) / 2;
        if (length > 0) {
            int fraction = bigEndianInteger(inputStream.read(length), 0, length);
            return fraction * (int) Math.pow(100, 3 - length);
        }
        return 0;
    }

    private int bigEndianInteger(byte[] bytes, int offset, int length) {
        int result = 0;
        for (int i = offset; i < (offset + length); i++) {
            byte b = bytes[i];
            result = (result << 8) | (b >= 0 ? (int) b : (b + 256));
        }
        return result;
    }

    private long bigEndianLong(byte[] bytes, int offset, int length) {
        long result = 0;
        for (int i = offset; i < (offset + length); i++) {
            byte b = bytes[i];
            result = (result << 8) | (b >= 0 ? (int) b : (b + 256));
        }
        return result;
    }

    private int bitSlice(long value, int bitOffset, int numberOfBits, int payloadSize) {
        long result = value >> payloadSize - (bitOffset + numberOfBits);
        return (int) (result & ((1 << numberOfBits) - 1));
    }

}
