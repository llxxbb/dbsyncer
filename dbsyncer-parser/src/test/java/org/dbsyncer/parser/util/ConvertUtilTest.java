package org.dbsyncer.parser.util;

import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.sdk.model.Convert;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

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
        ConvertUtil.convert(converts,  row);
        // 应该直接返回，不抛异常
    }

    @Test
    public void testConvertWithValidConvert() {
        // 测试有效的转换配置
        Convert convert = new Convert();
        convert.setName("test_field");
        convert.setConvertCode(ConvertEnum.UUID.getCode());
        convert.setArgs("");
        convert.setRoot(true);
        converts.add(convert);

        row.put("test_field", "original_value");

        ConvertUtil.convert(converts, row);

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
        convert.setConvertCode(ConvertEnum.TEMPLATE.getCode());
        convert.setArgs("F(first_name) F(last_name)");  // 简化表达式，空格自然保留
        convert.setRoot(true);
        converts.add(convert);

        // 先预解析模板（验证规则并缓存解析结果）
        ConvertUtil.validateFieldConverterRule(converts);

        row.put("first_name", "John");
        row.put("last_name", "Doe");

        ConvertUtil.convert(converts, row);

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
        convert.setRoot(true);
        converts.add(convert);

        ConvertUtil.convert(converts, row);

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
        convert.setRoot(true);
        converts.add(convert);

        // 当字段值为null时，应该使用默认值
        row.put("nullable_field", null);

        ConvertUtil.convert(converts, row);

        Object result = row.get("nullable_field");
        assertEquals("DEFAULT_VALUE", result);
    }

    @Test
    public void testConvertWithInvalidConvertCode() {
        // 测试无效转换代码
        Convert convert = new Convert();
        convert.setName("invalid_field");
        convert.setConvertCode("INVALID_CODE"); // 无效代码
        convert.setRoot(true);
        converts.add(convert);

        row.put("invalid_field", "test_value");

        // 应该跳过无效的转换代码而不抛异常
        try {
            ConvertUtil.convert(converts, row);
        } catch (Exception e) {
            fail("不应该抛出异常: " + e.getMessage());
        }
    }

    @Test
    public void testConvertSyncRowWithBeforeRow() {
        // 测试固定值转换器对 beforeRow 的影响
        Convert convert = new Convert();
        convert.setName("status");
        convert.setConvertCode(ConvertEnum.DEFAULT.getCode());
        convert.setArgs("ACTIVE");
        convert.setRoot(true);
        converts.add(convert);

        // 构建 SyncRow
        SyncRow syncRow = new SyncRow();
        Map<String, Object> data = new HashMap<>();
        data.put("id", 1);
        data.put("status", "PENDING");
        syncRow.setData(data);

        // 设置 beforeRow
        List<Object> beforeRow = new ArrayList<>();
        beforeRow.add(1);
        beforeRow.add(null);
        syncRow.setBeforeRow(beforeRow);

        // 设置 columnNames
        List<String> columnNames = new ArrayList<>();
        columnNames.add("id");
        columnNames.add("status");
        syncRow.setColumnNames(columnNames);

        // 执行转换
        List<SyncRow> rows = new ArrayList<>();
        rows.add(syncRow);
        ConvertUtil.convert(converts, rows);

        // 验证 data 被转换
        assertEquals("PENDING", syncRow.getData().get("status"));

        // 验证 beforeRow 也被转换
        assertEquals("ACTIVE", syncRow.getBeforeRow().get(1));
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