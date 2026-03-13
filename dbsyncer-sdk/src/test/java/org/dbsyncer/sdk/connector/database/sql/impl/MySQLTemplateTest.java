package org.dbsyncer.sdk.connector.database.sql.impl;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * MySQLTemplate 单元测试
 * 
 * 重点测试 ENUM 和 SET 类型的 DDL 生成
 */
public class MySQLTemplateTest {

    private MySQLTemplate mySQLTemplate;
    private MockSchemaResolver mockSchemaResolver;

    @Before
    public void setUp() {
        mockSchemaResolver = new MockSchemaResolver();
        mySQLTemplate = new MySQLTemplate(mockSchemaResolver);
    }

    // ==================== ENUM 类型测试 ====================

    @Test
    public void testConvertEnumType_WithValidValues() {
        // 测试 ENUM 类型生成正确的格式
        Field field = createEnumField("status", Arrays.asList("active", "inactive", "pending"));
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("ENUM('active', 'inactive', 'pending')", result);
    }

    @Test
    public void testConvertEnumType_WithSingleQuoteInValue() {
        // 测试枚举值中包含单引号时的转义
        Field field = createEnumField("type", Arrays.asList("it's", "don't", "can't"));
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        // 单引号应该被转义为 ''
        assertEquals("ENUM('it''s', 'don''t', 'can''t')", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertEnumType_WithNullValues() {
        // 测试 enumValues 为 null 时抛异常
        Field field = new Field("status", "ENUM", 12);
        field.setEnumValues(null);
        
        mySQLTemplate.convertToDatabaseType(field);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertEnumType_WithEmptyValues() {
        // 测试 enumValues 为空列表时抛异常
        Field field = new Field("status", "ENUM", 12);
        field.setEnumValues(Arrays.asList());
        
        mySQLTemplate.convertToDatabaseType(field);
    }

    // ==================== SET 类型测试 ====================

    @Test
    public void testConvertSetType_WithValidValues() {
        // 测试 SET 类型生成正确的格式
        Field field = createSetField("tags", Arrays.asList("news", "sports", "tech"));
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("SET('news', 'sports', 'tech')", result);
    }

    @Test
    public void testConvertSetType_WithSingleQuoteInValue() {
        // 测试集合值中包含单引号时的转义
        Field field = createSetField("options", Arrays.asList("A's", "B's"));
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("SET('A''s', 'B''s')", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertSetType_WithNullValues() {
        // 测试 setValues 为 null 时抛异常
        Field field = new Field("tags", "SET", 12);
        field.setEnumValues(null);
        
        mySQLTemplate.convertToDatabaseType(field);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConvertSetType_WithEmptyValues() {
        // 测试 setValues 为空列表时抛异常
        Field field = new Field("tags", "SET", 12);
        field.setEnumValues(Arrays.asList());
        
        mySQLTemplate.convertToDatabaseType(field);
    }

    // ==================== 其他类型测试（回归） ====================

    @Test
    public void testConvertVarcharType() {
        Field field = new Field("name", "VARCHAR", 12);
        field.setColumnSize(255);
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("VARCHAR(255)", result);
    }

    @Test
    public void testConvertIntType() {
        Field field = new Field("id", "INT", 4);
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("INT", result);
    }

    @Test
    public void testConvertDecimalType() {
        Field field = new Field("price", "DECIMAL", 3);
        field.setColumnSize(10);
        field.setRatio(2);
        
        String result = mySQLTemplate.convertToDatabaseType(field);
        
        assertEquals("DECIMAL(10,2)", result);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 ENUM 类型的 Field
     */
    private Field createEnumField(String name, List<String> enumValues) {
        Field field = new Field(name, "ENUM", 12);
        field.setEnumValues(enumValues);
        return field;
    }

    /**
     * 创建 SET 类型的 Field
     */
    private Field createSetField(String name, List<String> setValues) {
        Field field = new Field(name, "SET", 12);
        field.setEnumValues(setValues);
        return field;
    }

    // ==================== Mock SchemaResolver ====================

    /**
     * 模拟 SchemaResolver，用于测试
     * 直接返回输入的 Field，不做类型转换
     */
    private static class MockSchemaResolver implements SchemaResolver {
        @Override
        public Object merge(Object val, Field field) {
            return val;
        }

        @Override
        public Object convert(Object val, Field field) {
            return val;
        }

        @Override
        public Field fromStandardType(Field field) {
            // 测试中不需要实际的类型转换，直接返回原字段
            return field;
        }

        @Override
        public Field toStandardType(Field field) {
            return field;
        }
    }
}
