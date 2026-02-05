package org.dbsyncer.parser.util;

import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class ConvertUtilTest {

    private List<Convert> converts;
    private Map<String, Object> row;

    @Before
    public void setUp() {
        converts = new ArrayList<>();
        row = new HashMap<>();
    }

    @Test
    public void testConvertWithNullRow() {
        // 测试空数据行
        ConvertUtil.convert(converts, (Map)row);
        // 应该直接返回，不抛异常
    }

    @Test
    public void testConvertWithValidConvert() {
        // 测试有效的转换配置
        Convert convert = new Convert();
        convert.setName("test_field");
        convert.setConvertCode(ConvertEnum.UUID.getCode());
        convert.setArgs("");
        converts.add(convert);

        row.put("test_field", "original_value");

        ConvertUtil.convert(converts, (Map)row);

        // UUID转换后应该是UUID格式的字符串
        Object result = row.get("test_field");
        assertNotNull(result);
        assertTrue(isValidUUID(result.toString()));
    }

    @Test
    public void testConvertWithExpression() {
        // 测试表达式转换（简单字符串拼接）
        Convert convert = new Convert();
        convert.setName("full_name");
        convert.setConvertCode(ConvertEnum.EXPRESSION.getCode());
        convert.setArgs("F(first_name) F(last_name)");  // 简化表达式，空格自然保留
        converts.add(convert);

        row.put("first_name", "John");
        row.put("last_name", "Doe");

        ConvertUtil.convert(converts, (Map)row);

        Object result = row.get("full_name");
        assertEquals("John Doe", result);
    }

    @Test
    public void testConvertWithFixedValue() {
        // 测试固定值转换
        Convert convert = new Convert();
        convert.setName("constant_field");
        convert.setConvertCode(ConvertEnum.FIXED.getCode());
        convert.setArgs("CONSTANT_VALUE");
        converts.add(convert);

        ConvertUtil.convert(converts, (Map)row);

        Object result = row.get("constant_field");
        assertEquals("CONSTANT_VALUE", result);
    }

    @Test
    public void testConvertWithDefaultValue() {
        // 测试默认值转换
        Convert convert = new Convert();
        convert.setName("nullable_field");
        convert.setConvertCode(ConvertEnum.DEFAULT.getCode());
        convert.setArgs("DEFAULT_VALUE");
        converts.add(convert);

        // 当字段值为null时，应该使用默认值
        row.put("nullable_field", null);

        ConvertUtil.convert(converts, (Map)row);

        Object result = row.get("nullable_field");
        assertEquals("DEFAULT_VALUE", result);
    }

    @Test
    public void testConvertWithInvalidConvertCode() {
        // 测试无效转换代码
        Convert convert = new Convert();
        convert.setName("invalid_field");
        convert.setConvertCode("INVALID_CODE"); // 无效代码
        converts.add(convert);

        row.put("invalid_field", "test_value");

        // 应该跳过无效的转换代码而不抛异常
        try {
            ConvertUtil.convert(converts, (Map)row);
        } catch (Exception e) {
            fail("不应该抛出异常: " + e.getMessage());
        }
    }

    private boolean isValidUUID(String uuid) {
        try {
            // UUIDUtil返回的是不带连字符的UUID，所以需要先规范化
            String normalizedUUID;
            if (uuid.length() == 32) {
                // 如果是32位的不带连字符的UUID，添加连字符
                normalizedUUID = uuid.replaceAll(
                    "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                    "$1-$2-$3-$4-$5"
                );
            } else {
                normalizedUUID = uuid;
            }
            UUID.fromString(normalizedUUID);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}