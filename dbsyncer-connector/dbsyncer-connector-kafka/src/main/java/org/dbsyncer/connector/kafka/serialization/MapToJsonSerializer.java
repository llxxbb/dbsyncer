/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.kafka.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Kafka消息JSON序列化器
 * 支持日期类型序列化为字符串格式（保留纳秒精度）
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2021-12-16 23:09
 */
public class MapToJsonSerializer implements Serializer<Map> {
    private String encoding = "UTF8";

    private static final ObjectMapper KAFKA_OBJECT_MAPPER = new ObjectMapper();
    
    static {
        KAFKA_OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        KAFKA_OBJECT_MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        SimpleModule dateModule = new SimpleModule();
        dateModule.addSerializer(Timestamp.class, new TimestampSerializer());
        dateModule.addSerializer(Date.class, new DateSerializer());
        dateModule.addSerializer(java.sql.Date.class, new SqlDateSerializer());
        KAFKA_OBJECT_MAPPER.registerModule(dateModule);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String propertyName = isKey ? "key.serializer.encoding" : "value.serializer.encoding";
        Object encodingValue = configs.get(propertyName);
        if (encodingValue == null)
            encodingValue = configs.get("serializer.encoding");
        if (encodingValue != null && encodingValue instanceof String)
            encoding = (String) encodingValue;
    }

    @Override
    public byte[] serialize(String topic, Map data) {
        try {
            if (data == null)
                return null;
            else
                return KAFKA_OBJECT_MAPPER.writeValueAsString(data).getBytes(encoding);
        } catch (UnsupportedEncodingException e) {
            throw new SerializationException("Error when serializing string to byte[] due to unsupported encoding " + encoding);
        } catch (Exception e) {
            throw new SerializationException("Error when serializing data: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // nothing to do
    }

    private static class TimestampSerializer extends JsonSerializer<Timestamp> {
        @Override
        public void serialize(Timestamp value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            String formatted = formatTimestampWithNanos(value);
            gen.writeString(formatted);
        }
    }

    private static class DateSerializer extends JsonSerializer<Date> {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(DATE_FORMAT.format(value));
        }
    }

    private static class SqlDateSerializer extends JsonSerializer<java.sql.Date> {
        private static final SimpleDateFormat SQL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
        
        @Override
        public void serialize(java.sql.Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(SQL_DATE_FORMAT.format(value));
        }
    }

    private static String formatTimestampWithNanos(Timestamp timestamp) {
        String tsString = timestamp.toString();
        return tsString;
    }
}
