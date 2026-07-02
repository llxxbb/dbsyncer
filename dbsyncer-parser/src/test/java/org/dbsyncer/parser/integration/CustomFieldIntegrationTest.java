package org.dbsyncer.parser.integration;

import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.sdk.model.Convert;
import org.dbsyncer.sdk.model.FieldMapping;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.parser.model.Picker;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.parser.util.PickerUtil;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.*;

public class CustomFieldIntegrationTest {

    private Mapping mapping;
    private TableGroup tableGroup;

    @Before
    public void setUp() {
        // 初始化映射配置
        mapping = new Mapping();
        mapping.setConvert(new ArrayList<>()); // 初始化转换配置列表
        
        // 初始化监听器，避免NPE
        org.dbsyncer.sdk.config.ListenerConfig listener = new org.dbsyncer.sdk.config.ListenerConfig();
        listener.setEventFieldName(null); // 不设置事件字段名，避免检查
        mapping.setListener(listener);

        // 初始化表组
        tableGroup = new TableGroup();
        tableGroup.setFieldMapping(new ArrayList<>());
        tableGroup.setConvert(new ArrayList<>());

        // 设置源表和目标表
        setupTables();
    }

    private void setupTables() {
        // 设置源表结构
        List<Field> sourceColumns = new ArrayList<>();
        Field firstNameField = new Field();
        firstNameField.setName("first_name");
        firstNameField.setTypeName("VARCHAR");
        sourceColumns.add(firstNameField);

        Field lastNameField = new Field();
        lastNameField.setName("last_name");
        lastNameField.setTypeName("VARCHAR");
        sourceColumns.add(lastNameField);

        Field ageField = new Field();
        ageField.setName("age");
        ageField.setTypeName("INTEGER");
        sourceColumns.add(ageField);

        Field statusField = new Field();
        statusField.setName("status");
        statusField.setTypeName("VARCHAR");
        sourceColumns.add(statusField);

        // 设置目标表结构
        List<Field> targetColumns = new ArrayList<>();
        Field targetFirstNameField = new Field();
        targetFirstNameField.setName("first_name");
        targetFirstNameField.setTypeName("VARCHAR");
        targetColumns.add(targetFirstNameField);

        Field targetLastNameField = new Field();
        targetLastNameField.setName("last_name");
        targetLastNameField.setTypeName("VARCHAR");
        targetColumns.add(targetLastNameField);

        Field fullNameField = new Field();
        fullNameField.setName("full_name");
        fullNameField.setTypeName("VARCHAR");
        targetColumns.add(fullNameField);

        Field calculatedAgeField = new Field();
        calculatedAgeField.setName("calculated_age");
        calculatedAgeField.setTypeName("INTEGER");
        targetColumns.add(calculatedAgeField);

        Field constantField = new Field();
        constantField.setName("sync_batch_id");
        constantField.setTypeName("VARCHAR");
        targetColumns.add(constantField);

        Field targetStatusField = new Field();
        targetStatusField.setName("status");
        targetStatusField.setTypeName("VARCHAR");
        targetColumns.add(targetStatusField);

        Field timestampField = new Field();
        timestampField.setName("created_time");
        timestampField.setTypeName("TIMESTAMP");
        targetColumns.add(timestampField);

        MetaInfo sourceTable = new MetaInfo("source_table", null, null, null, null, null);
        sourceTable.setColumn(sourceColumns);

        MetaInfo targetTable = new MetaInfo("target_table", null, null, null, null, null);
        targetTable.setColumn(targetColumns);

        tableGroup.setSourceTable(sourceTable);
        tableGroup.setTargetTable(targetTable);

        // 设置字段映射（只映射基础字段，自定义字段将在转换中处理）
        List<FieldMapping> fieldMappings = new ArrayList<>();
        fieldMappings.add(new FieldMapping(firstNameField, targetFirstNameField));
        fieldMappings.add(new FieldMapping(lastNameField, targetLastNameField));
        fieldMappings.add(new FieldMapping(ageField, calculatedAgeField));
        tableGroup.setFieldMapping(fieldMappings);

        // 添加转换配置
        List<Convert> converts = new ArrayList<>();

        // 1. 表达式转换：创建全名（根转换器）
        Convert fullNameConvert = new Convert();
        fullNameConvert.setName("full_name");
        fullNameConvert.setConvertCode(ConvertEnum.TEMPLATE.getCode());
        fullNameConvert.setArgs("F(first_name) F(last_name)");
        fullNameConvert.setRoot(true);
        converts.add(fullNameConvert);

        // 2. 固定值转换：设置批次ID（根转换器）
        Convert batchIdConvert = new Convert();
        batchIdConvert.setName("sync_batch_id");
        batchIdConvert.setConvertCode(ConvertEnum.FIXED.getCode());
        batchIdConvert.setArgs("BATCH_001");
        batchIdConvert.setRoot(true);
        converts.add(batchIdConvert);

        // 3. 默认值转换：设置状态（根转换器）
        Convert statusConvert = new Convert();
        statusConvert.setName("status");
        statusConvert.setConvertCode(ConvertEnum.DEFAULT.getCode());
        statusConvert.setArgs("ACTIVE");
        statusConvert.setRoot(true);
        converts.add(statusConvert);

        // 4. UUID转换：生成唯一ID（根转换器）
        Convert uuidConvert = new Convert();
        uuidConvert.setName("created_time");
        uuidConvert.setConvertCode(ConvertEnum.UUID.getCode());
        uuidConvert.setRoot(true);
        converts.add(uuidConvert);

        tableGroup.setConvert(converts);
    }

    @Test
    public void testCompleteCustomFieldFlow() {
        // 测试完整的自定义字段流程
        // 准备测试数据
        List<Map> sourceData = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();
        record.put("first_name", "John");
        record.put("last_name", "Doe");
        record.put("age", 30);
        record.put("status", null); // 用于测试默认值
        sourceData.add(record);

        // 1. 合并配置
        TableGroup mergedGroup = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);

        // 2. 预解析模板（必须在转换前调用）
        ConvertUtil.validateFieldConverterRule(tableGroup.getConvert());

        // 3. 创建Picker进行字段映射
        Picker picker = new Picker(mergedGroup);

        // 4. 执行字段映射
        List<SyncRow> targetSyncRows = picker.pickTargetData(sourceData, null);

        // 5. 执行转换（自定义字段处理）
        ConvertUtil.convert(tableGroup.getConvert(), targetSyncRows);

        // 6. 验证结果
        // 从 SyncRow 中提取数据 Map
        List<Map> targetData = targetSyncRows.stream()
                .map(SyncRow::getData)
                .collect(java.util.stream.Collectors.toList());

        // 验证基础字段映射
        assertEquals("John", targetData.get(0).get("first_name"));
        assertEquals("Doe", targetData.get(0).get("last_name"));
        assertEquals(30, targetData.get(0).get("calculated_age"));

        // 验证自定义字段
        assertEquals("John Doe", targetData.get(0).get("full_name")); // 表达式转换
        assertEquals("BATCH_001", targetData.get(0).get("sync_batch_id")); // 固定值转换
        assertEquals("ACTIVE", targetData.get(0).get("status")); // 默认值转换
        assertNotNull(targetData.get(0).get("created_time")); // UUID转换，应生成非空值
        assertTrue(isValidUUID(targetData.get(0).get("created_time").toString())); // 验证UUID格式
    }

    @Test
    public void testMultipleRecordsCustomFieldFlow() {
        // 测试多条记录的自定义字段流程
        // 准备测试数据
        List<Map> sourceData = new ArrayList<>();
        Map<String, Object> record1 = new HashMap<>();
        record1.put("first_name", "John");
        record1.put("last_name", "Doe");
        record1.put("age", 30);
        record1.put("status", null);
        sourceData.add(record1);

        Map<String, Object> record2 = new HashMap<>();
        record2.put("first_name", "Jane");
        record2.put("last_name", "Smith");
        record2.put("age", 25);
        record2.put("status", null);
        sourceData.add(record2);

        // 1. 合并配置
        TableGroup mergedGroup = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);

        // 2. 预解析模板（必须在转换前调用）
        ConvertUtil.validateFieldConverterRule(tableGroup.getConvert());

        // 3. 创建Picker进行字段映射
        Picker picker = new Picker(mergedGroup);

        // 4. 执行字段映射
        List<SyncRow> targetSyncRows = picker.pickTargetData(sourceData, null);

        // 5. 执行转换（自定义字段处理）
        ConvertUtil.convert(tableGroup.getConvert(), targetSyncRows);

        // 6. 验证结果
        List<Map> targetData = targetSyncRows.stream()
                .map(SyncRow::getData)
                .collect(java.util.stream.Collectors.toList());

        assertEquals(2, targetData.size());

        // 验证第一条记录
        Map<String, Object> result1 = targetData.get(0);
        assertEquals("John Doe", result1.get("full_name"));
        assertEquals("BATCH_001", result1.get("sync_batch_id"));
        assertEquals("ACTIVE", result1.get("status"));

        // 验证第二条记录
        Map<String, Object> result2 = targetData.get(1);
        assertEquals("Jane Smith", result2.get("full_name"));
        assertEquals("BATCH_001", result2.get("sync_batch_id"));
        assertEquals("ACTIVE", result2.get("status"));

        // 验证每条记录的UUID是唯一的
        assertNotEquals(result1.get("created_time"), result2.get("created_time"));
    }

    @Test
    public void testEdgeCases() {
        // 测试边界情况
        // 创建一个包含null和空值的数据集
        List<Map> edgeCaseData = new ArrayList<>();
        Map<String, Object> nullRecord = new HashMap<>();
        nullRecord.put("first_name", null);
        nullRecord.put("last_name", "");
        nullRecord.put("age", null);
        nullRecord.put("status", null);
        edgeCaseData.add(nullRecord);

        // 合并配置
        TableGroup mergedGroup = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);

        // 预解析模板（必须在转换前调用）
        ConvertUtil.validateFieldConverterRule(tableGroup.getConvert());

        // 创建Picker
        Picker picker = new Picker(mergedGroup);

        // 执行字段映射
        List<SyncRow> targetSyncRows = picker.pickTargetData(edgeCaseData, null);

        // 执行转换
        ConvertUtil.convert(tableGroup.getConvert(), targetSyncRows);

        // 验证结果
        List<Map> targetData = targetSyncRows.stream()
                .map(SyncRow::getData)
                .collect(java.util.stream.Collectors.toList());

        assertEquals(1, targetData.size());
        Map<String, Object> result = targetData.get(0);

        // 对于表达式 "F(first_name) F(last_name)"
        // 当first_name为null时转换为""，last_name为""，所以结果为 "" + " " + "" = " "
        String fullName = (String) result.get("full_name");
        assertTrue(fullName != null && fullName.length() <= 1); // 可能是空格

        // 固定值应该仍然存在
        assertEquals("BATCH_001", result.get("sync_batch_id"));

        // 默认值应该应用
        assertEquals("ACTIVE", result.get("status"));

        // UUID应该生成
        assertNotNull(result.get("created_time"));
    }

    @Test
    public void testKafkaTargetWithEmptyTargetColumns() {
        // 复现 bug：以 Kafka 等无 Schema 连接器为目标端时，
        // targetTable.getColumn() 为空，原本会导致非 converter 字段丢失。
        // 预期：SyncRow 应包含所有 fieldMapping 字段（包括非 converter 字段）。

        // 重新初始化一个 TableGroup，目标表列列表为空（模拟 Kafka 场景）
        TableGroup kafkaGroup = new TableGroup();
        kafkaGroup.setConvert(new ArrayList<>());

        // 源表：有完整列定义
        MetaInfo sourceTable = new MetaInfo();
        List<Field> sourceColumns = new ArrayList<>();
        String[] srcNames = {"col_a", "col_b", "col_c", "col_d", "col_e"};
        for (String name : srcNames) {
            Field f = new Field();
            f.setName(name);
            f.setTypeName("VARCHAR");
            sourceColumns.add(f);
        }
        sourceTable.setColumn(sourceColumns);

        // 目标表：空列列表（Kafka 行为）
        MetaInfo targetTable = new MetaInfo();
        targetTable.setColumn(new ArrayList<>());

        kafkaGroup.setSourceTable(sourceTable);
        kafkaGroup.setTargetTable(targetTable);

        // 5 个字段映射
        List<FieldMapping> fieldMappings = new ArrayList<>();
        fieldMappings.add(new FieldMapping("col_a", "tgt_a"));
        fieldMappings.add(new FieldMapping("col_b", "tgt_b"));
        fieldMappings.add(new FieldMapping("col_c", "tgt_c"));
        fieldMappings.add(new FieldMapping("col_d", "tgt_d"));
        fieldMappings.add(new FieldMapping("col_e", "tgt_e"));
        kafkaGroup.setFieldMapping(fieldMappings);

        // 仅为 2 个字段配置 converter
        List<Convert> converts = new ArrayList<>();
        Convert convA = new Convert();
        convA.setName("tgt_a");
        convA.setConvertCode(ConvertEnum.FIXED.getCode());
        convA.setArgs("FIXED_VALUE");
        convA.setRoot(true);
        converts.add(convA);

        Convert convB = new Convert();
        convB.setName("tgt_b");
        convB.setConvertCode(ConvertEnum.DEFAULT.getCode());
        convB.setArgs("DEFAULT_VALUE");
        convB.setRoot(true);
        converts.add(convB);
        kafkaGroup.setConvert(converts);

        ConvertUtil.validateFieldConverterRule(converts);

        // 准备源数据
        List<Map> sourceData = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();
        record.put("col_a", "raw_a");
        record.put("col_b", "raw_b");
        record.put("col_c", "raw_c");
        record.put("col_d", "raw_d");
        record.put("col_e", "raw_e");
        sourceData.add(record);

        // 合并配置 + 创建 Picker + 执行映射
        TableGroup merged = PickerUtil.mergeTableGroupConfig(mapping, kafkaGroup);
        Picker picker = new Picker(merged);
        List<SyncRow> rows = picker.pickTargetData(sourceData, null);

        // 执行 converter
        ConvertUtil.convert(merged.getConvert(), rows);

        // 验证结果
        assertEquals("应生成 1 条 SyncRow", 1, rows.size());
        Map<String, Object> data = rows.get(0).getData();

        // 所有 5 个字段都应该存在（之前 bug 只保留了 converter 定义的 2 个字段）
        assertEquals("字段数应与 fieldMapping 数一致", 5, data.size());
        assertEquals("FIXED_VALUE", data.get("tgt_a"));   // FIXED converter 覆盖
        assertEquals("raw_b", data.get("tgt_b"));         // DEFAULT converter：值非空时保留原值
        assertEquals("raw_c", data.get("tgt_c"));         // 非 converter 字段
        assertEquals("raw_d", data.get("tgt_d"));         // 非 converter 字段
        assertEquals("raw_e", data.get("tgt_e"));         // 非 converter 字段
    }

    private boolean isValidUUID(String uuid) {
        try {
            // 尝试解析标准UUID格式（带连字符）
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            // 如果失败，尝试标准化不带连字符的UUID格式
            try {
                // 如果是32位十六进制字符串（不带连字符的UUID），将其标准化为带连字符的格式
                if (uuid != null && uuid.length() == 32) {
                    String standardizedUUID = uuid.replaceAll(
                        "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                        "$1-$2-$3-$4-$5"
                    );
                    UUID.fromString(standardizedUUID);
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                // 如果都不行，则不是有效UUID
            }
            return false;
        }
    }
}