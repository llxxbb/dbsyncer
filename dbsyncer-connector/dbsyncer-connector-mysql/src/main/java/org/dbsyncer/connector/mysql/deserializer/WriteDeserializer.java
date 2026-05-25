/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.deserializer;

import com.github.shyiko.mysql.binlog.event.TableMapEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.WriteRowsEventDataDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2025-04-12 15:21
 */
public final class WriteDeserializer extends WriteRowsEventDataDeserializer {

    private final DatetimeV2Deserialize datetimeV2Deserialize = new DatetimeV2Deserialize();
    private final JsonBinaryDeserialize jsonBinaryDeserialize = new JsonBinaryDeserialize();

    public WriteDeserializer(Map<Long, TableMapEventData> tableMapEventByTableId) {
        super(tableMapEventByTableId);
        setDeserializeCharAndBinaryAsByteArray(this, true);
    }

    private static void setDeserializeCharAndBinaryAsByteArray(Object target, boolean value) {
        try {
            Class<?> clazz = target.getClass().getSuperclass();
            while (clazz != null) {
                try {
                    Method m = clazz.getDeclaredMethod("setDeserializeCharAndBinaryAsByteArray", boolean.class);
                    m.setAccessible(true);
                    m.invoke(target, value);
                    return;
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set deserializeCharAndBinaryAsByteArray", e);
        }
    }

    protected byte[] deserializeJson(int meta, ByteArrayInputStream inputStream) throws IOException {
        return jsonBinaryDeserialize.deserializeJson(meta, inputStream);
    }

    @Override
    protected Serializable deserializeDatetimeV2(int meta, ByteArrayInputStream inputStream) throws IOException {
        return datetimeV2Deserialize.deserializeDatetimeV2(meta, inputStream);
    }
}
