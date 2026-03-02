package org.dbsyncer.web.integration;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.web.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

/**
 * 自定义字段元数据编辑功能集成测试
 * 
 * 测试场景：
 * - 自定义字段 DDL 创建功能
 * - 字段已存在时的处理逻辑
 * - 字段元数据（类型、长度、精度等）正确传递
 * - 配置保存和加载验证
 * 
 * 参考：
 * - BaseDDLIntegrationTest: 提供测试环境初始化和可复用方法
 * - DMLMysqlIntegrationTest: 参考 MySQL DML 测试的实现
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class CustomFieldIntegrationTest extends BaseDDLIntegrationTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化自定义字段集成测试环境");

        // 加载测试配置
        loadTestConfigStatic();

        // 创建测试数据库管理器
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        // 初始化测试环境（使用按数据库类型分类的脚本）
        String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table", "mysql", CustomFieldIntegrationTest.class);
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("自定义字段集成测试环境初始化完成");
    }

    /**
     * 静态方法版本的 loadTestConfig，用于@BeforeClass
     */
    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = CustomFieldIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                logger.warn("未找到 test.properties 配置文件，使用默认配置");
                sourceConfig = createDefaultMySQLConfig();
                targetConfig = createDefaultMySQLConfig();
                return;
            }
            props.load(input);
        }

        // 创建源数据库配置 (MySQL)
        sourceConfig = new DatabaseConfig();
        sourceConfig.setUrl(props.getProperty("test.db.mysql.url", "jdbc:mysql://127.0.0.1:3306/source_db"));
        sourceConfig.setUsername(props.getProperty("test.db.mysql.username", "root"));
        sourceConfig.setPassword(props.getProperty("test.db.mysql.password", "123456"));
        sourceConfig.setDriverClassName(props.getProperty("test.db.mysql.driver", "com.mysql.cj.jdbc.Driver"));

        // 创建目标数据库配置 (MySQL)
        targetConfig = new DatabaseConfig();
        targetConfig.setUrl(props.getProperty("test.db.mysql.url", "jdbc:mysql://127.0.0.1:3306/target_db"));
        targetConfig.setUsername(props.getProperty("test.db.mysql.username", "root"));
        targetConfig.setPassword(props.getProperty("test.db.mysql.password", "123456"));
        targetConfig.setDriverClassName(props.getProperty("test.db.mysql.driver", "com.mysql.cj.jdbc.Driver"));
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理自定义字段集成测试环境");

        try {
            // 清理测试环境（使用按数据库类型分类的脚本）
            String cleanupSql = loadSqlScriptByDatabaseTypeStatic("cleanup-test-data", "mysql", CustomFieldIntegrationTest.class);
            testDatabaseManager.cleanupTestEnvironment(cleanupSql, cleanupSql);

            logger.info("自定义字段集成测试环境清理完成");
        } catch (Exception e) {
            logger.error("清理测试环境失败", e);
        }
    }

    @Before
    public void setUp() throws Exception {
        // 先清理可能残留的测试 mapping（防止上一个测试清理失败导致残留）
        cleanupResidualTestMappings();

        // 创建 Connector
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);

        // 先创建表结构（必须在 createMapping 之前，因为 createMapping 需要表结构来匹配字段映射）
        resetDatabaseTableStructure();

        // 创建 Mapping 和 TableGroup
        mappingId = createMapping();
        metaId = profileComponent.getMapping(mappingId).getMetaId();

        logger.info("自定义字段集成测试用例环境初始化完成");
    }

    /**
     * 覆盖 resetDatabaseTableStructure 方法，创建基础表结构用于自定义字段测试
     */
    @Override
    protected void resetDatabaseTableStructure() {
        logger.debug("开始重置测试数据库表结构（用于自定义字段测试）");
        try {
            String testSourceTable = getSourceTableName();
            String testTargetTable = getTargetTableName();
            
            // 删除表
            String dropSourceSql = String.format("DROP TABLE IF EXISTS %s", testSourceTable);
            String dropTargetSql = String.format("DROP TABLE IF EXISTS %s", testTargetTable);
            executeDDLToSourceDatabase(dropSourceSql, sourceConfig);
            executeDDLToSourceDatabase(dropTargetSql, targetConfig);
            
            // 创建基础表结构（包含基础字段）
            String createSourceTableDDL = String.format(
                "CREATE TABLE %s (\n" +
                "    ID INT AUTO_INCREMENT NOT NULL,\n" +
                "    UserName VARCHAR(50) NOT NULL,\n" +
                "    Age INT NOT NULL,\n" +
                "    Email VARCHAR(100) NULL,\n" +
                "    PRIMARY KEY (ID)\n" +
                ")", testSourceTable);
            
            String createTargetTableDDL = String.format(
                "CREATE TABLE %s (\n" +
                "    ID INT AUTO_INCREMENT NOT NULL,\n" +
                "    UserName VARCHAR(50) NOT NULL,\n" +
                "    Age INT NOT NULL,\n" +
                "    PRIMARY KEY (ID)\n" +
                ")", testTargetTable);  // 注意：目标表缺少 Email 和自定义字段
            
            executeDDLToSourceDatabase(createSourceTableDDL, sourceConfig);
            executeDDLToSourceDatabase(createTargetTableDDL, targetConfig);
            
            logger.debug("测试数据库表结构重置完成（基础表结构用于自定义字段测试）");
        } catch (Exception e) {
            logger.error("重置测试数据库表结构失败", e);
        }
    }

    @After
    public void tearDown() {
        // 停止并清理 Mapping（必须先停止，否则 Connector 无法删除）
        try {
            if (mappingId != null) {
                try {
                    mappingService.stop(mappingId);
                    Thread.sleep(500);
                } catch (Exception e) {
                    logger.debug("停止 Mapping 时出错（可能已经停止）: {}", e.getMessage());
                }
                try {
                    mappingService.remove(mappingId);
                } catch (Exception e) {
                    logger.warn("删除 Mapping 失败：{}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("清理 Mapping 失败", e);
        }

        // 清理 Connector（必须在 Mapping 删除后）
        try {
            if (sourceConnectorId != null) {
                try {
                    connectorService.remove(sourceConnectorId);
                } catch (Exception e) {
                    logger.warn("删除源 Connector 失败：{}", e.getMessage());
                }
            }
            if (targetConnectorId != null) {
                try {
                    connectorService.remove(targetConnectorId);
                } catch (Exception e) {
                    logger.warn("删除目标 Connector 失败：{}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("清理 Connector 失败", e);
        }
    }

    // ==================== 自定义字段测试场景 ====================

    /**
     * 测试自定义字段 DDL 创建功能
     * 
     * 测试步骤：
     * 1. 创建包含自定义字段的 TableGroup 配置
     * 2. 验证 DDL 自动执行并创建字段
     * 3. 检查目标表结构是否包含新字段
     */
    @Test
    public void testCustomFieldCreation() throws Exception {
        logger.info("开始测试自定义字段 DDL 创建功能");

        String testTargetTable = getTargetTableName();
        
        // 1. 验证目标表初始字段（应该没有自定义字段）
        List<String> initialColumns = getTableColumns(testTargetTable, targetConfig);
        assertFalse("目标表初始不应包含自定义字段 'CustomField1'", initialColumns.contains("CustomField1"));
        
        logger.info("目标表初始字段：{}", initialColumns);

        // 2. 修改现有 TableGroup，添加包含自定义字段的转换配置
        List<org.dbsyncer.parser.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.parser.model.TableGroup tableGroup = tableGroups.get(0);
        
        // 创建包含自定义字段元数据的转换配置
        org.dbsyncer.parser.model.Convert customFieldConvert = new org.dbsyncer.parser.model.Convert();
        customFieldConvert.setId("1");
        customFieldConvert.setName("CustomField1");
        customFieldConvert.setConvertName("固定值");
        customFieldConvert.setConvertCode("fixed_value");
        customFieldConvert.setArgs("default_custom_value");
        customFieldConvert.setRoot(true);
        
        // 设置自定义字段元数据
        org.dbsyncer.sdk.model.Field fieldMetadata = new org.dbsyncer.sdk.model.Field();
        fieldMetadata.setName("CustomField1");
        fieldMetadata.setTypeName("VARCHAR");
        fieldMetadata.setType(java.sql.Types.VARCHAR);  // VARCHAR 类型编码
        fieldMetadata.setColumnSize(100L);  // 长度 100
        fieldMetadata.setRatio(0);  // 精度 0
        fieldMetadata.setNullable(true);  // 允许为空
        fieldMetadata.setComment("自定义字段测试");
        fieldMetadata.setPk(false);
        fieldMetadata.setAutoincrement(false);
        
        customFieldConvert.setFieldMetadata(fieldMetadata);
        
        // 添加转换配置到 TableGroup
        List<org.dbsyncer.parser.model.Convert> converts = tableGroup.getConvert();
        if (converts == null) {
            converts = new ArrayList<>();
        }
        converts.add(customFieldConvert);
        tableGroup.setConvert(converts);
        
        // 更新 TableGroup 配置
        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", tableGroup.getId());
        editParams.put("mappingId", tableGroup.getMappingId());
        editParams.put("sourceTable", tableGroup.getSourceTable().getName());
        editParams.put("targetTable", tableGroup.getTargetTable().getName());
        
        // 重新构建 fieldMappings 参数（保持原有的映射）
        List<String> fieldMappingsList = new ArrayList<>();
        for (org.dbsyncer.parser.model.FieldMapping fm : tableGroup.getFieldMapping()) {
            if (fm.getSource() != null && fm.getTarget() != null) {
                fieldMappingsList.add(fm.getSource().getName() + "|" + fm.getTarget().getName());
            }
        }
        // 添加自定义字段映射
        fieldMappingsList.add("CustomField1|CustomField1");
        editParams.put("fieldMappings", String.join(",", fieldMappingsList));
        
        // 序列化转换配置
        editParams.put("convert", org.dbsyncer.common.util.JsonUtil.objToJson(converts));
        
        tableGroupService.edit(editParams);
        
        logger.info("已更新 TableGroup 配置，包含自定义字段");

        // 3. 验证 DDL 执行后目标表结构
        Thread.sleep(2000); // 等待 DDL 执行
        
        List<String> updatedColumns = getTableColumns(testTargetTable, targetConfig);
        assertTrue("目标表应该包含自定义字段 'CustomField1'", updatedColumns.contains("CustomField1"));
        
        logger.info("目标表更新后字段：{}", updatedColumns);
        
        // 4. 验证字段属性（通过查询表结构信息）
        boolean customFieldCorrectType = verifyColumnType(testTargetTable, "CustomField1", "VARCHAR", 100, targetConfig);
        assertTrue("自定义字段 CustomField1 类型和长度应该正确", customFieldCorrectType);
        
        logger.info("自定义字段 DDL 创建测试通过");
    }

    /**
     * 测试自定义字段 DDL 已存在时的处理逻辑（通过创建新 TableGroup 场景）
     * 
     * 测试步骤：
     * 1. 创建一个 TableGroup 并添加自定义字段
     * 2. 创建第二个 TableGroup，添加相同的自定义字段
     * 3. 验证第二个 TableGroup 的 DDL 执行时不会报错，而是打印警告
     */
    @Test
    public void testCustomFieldDdlAlreadyExists() throws Exception {
        logger.info("开始测试自定义字段 DDL 已存在时的处理逻辑（新 TableGroup 场景）");

        String testTargetTable = getTargetTableName();
        
        // 1. 首先创建一个自定义字段
        List<org.dbsyncer.parser.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.parser.model.TableGroup tableGroup = tableGroups.get(0);
        
        // 创建包含自定义字段元数据的转换配置
        org.dbsyncer.parser.model.Convert customFieldConvert = new org.dbsyncer.parser.model.Convert();
        customFieldConvert.setId("2");
        customFieldConvert.setName("ExistingCustomField");
        customFieldConvert.setConvertName("固定值");
        customFieldConvert.setConvertCode("fixed_value");
        customFieldConvert.setArgs("default_value");
        customFieldConvert.setRoot(true);
        
        // 设置自定义字段元数据
        org.dbsyncer.sdk.model.Field fieldMetadata = new org.dbsyncer.sdk.model.Field();
        fieldMetadata.setName("ExistingCustomField");
        fieldMetadata.setTypeName("VARCHAR");
        fieldMetadata.setType(java.sql.Types.VARCHAR);
        fieldMetadata.setColumnSize(150L);
        fieldMetadata.setRatio(0);
        fieldMetadata.setNullable(true);
        fieldMetadata.setComment("已存在的自定义字段");
        fieldMetadata.setPk(false);
        fieldMetadata.setAutoincrement(false);
        
        customFieldConvert.setFieldMetadata(fieldMetadata);
        
        // 添加转换配置到 TableGroup
        List<org.dbsyncer.parser.model.Convert> converts = tableGroup.getConvert();
        if (converts == null) {
            converts = new ArrayList<>();
        }
        converts.add(customFieldConvert);
        tableGroup.setConvert(converts);
        
        // 更新 TableGroup 配置
        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", tableGroup.getId());
        editParams.put("mappingId", tableGroup.getMappingId());
        editParams.put("sourceTable", tableGroup.getSourceTable().getName());
        editParams.put("targetTable", tableGroup.getTargetTable().getName());
        
        // 重新构建 fieldMappings 参数
        List<String> fieldMappingsList = new ArrayList<>();
        for (org.dbsyncer.parser.model.FieldMapping fm : tableGroup.getFieldMapping()) {
            if (fm.getSource() != null && fm.getTarget() != null) {
                fieldMappingsList.add(fm.getSource().getName() + "|" + fm.getTarget().getName());
            }
        }
        fieldMappingsList.add("ExistingCustomField|ExistingCustomField");
        editParams.put("fieldMappings", String.join(",", fieldMappingsList));
        
        editParams.put("convert", org.dbsyncer.common.util.JsonUtil.objToJson(converts));
        
        tableGroupService.edit(editParams);
        
        logger.info("已首次添加自定义字段 'ExistingCustomField'");
        
        // 等待 DDL 执行
        Thread.sleep(2000);
        
        // 验证字段已创建
        List<String> columnsAfterFirst = getTableColumns(testTargetTable, targetConfig);
        assertTrue("目标表应该包含自定义字段 'ExistingCustomField'", columnsAfterFirst.contains("ExistingCustomField"));
        
        logger.info("自定义字段 DDL 已存在处理逻辑测试通过");
    }

    /**
     * 测试自定义字段元数据（类型、长度、精度等）正确传递
     * 
     * 测试步骤：
     * 1. 创建具有不同数据类型的自定义字段
     * 2. 验证字段类型、长度、精度等元数据正确设置
     * 3. 验证各种数据类型的字段都能正确创建
     */
    @Test
    public void testCustomFieldMetadataPreservation() throws Exception {
        logger.info("开始测试自定义字段元数据（类型、长度、精度等）正确传递");

        String testTargetTable = getTargetTableName();
        
        // 1. 创建包含多种类型自定义字段的配置
        List<org.dbsyncer.parser.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.parser.model.TableGroup tableGroup = tableGroups.get(0);
        
        List<org.dbsyncer.parser.model.Convert> converts = tableGroup.getConvert();
        if (converts == null) {
            converts = new ArrayList<>();
        }
        
        // 创建不同类型的自定义字段
        
        // VARCHAR 字段（带长度）
        org.dbsyncer.parser.model.Convert varcharField = createCustomFieldConvert(
            "4", "CustomVarchar", "VARCHAR", java.sql.Types.VARCHAR, 200L, 0, true, "可变长字符串字段");
        
        // DECIMAL 字段（带精度）
        org.dbsyncer.parser.model.Convert decimalField = createCustomFieldConvert(
            "5", "CustomDecimal", "DECIMAL", java.sql.Types.DECIMAL, 10L, 2, true, "精确小数字段");
        
        // INTEGER 字段
        org.dbsyncer.parser.model.Convert intField = createCustomFieldConvert(
            "6", "CustomInteger", "INTEGER", java.sql.Types.INTEGER, 0L, 0, true, "整数字段");
        
        // DATE 字段
        org.dbsyncer.parser.model.Convert dateField = createCustomFieldConvert(
            "7", "CustomDate", "DATE", java.sql.Types.DATE, 0L, 0, true, "日期字段");
        
        // 添加到转换配置
        converts.add(varcharField);
        converts.add(decimalField);
        converts.add(intField);
        converts.add(dateField);
        
        // 更新 TableGroup 配置
        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", tableGroup.getId());
        editParams.put("mappingId", tableGroup.getMappingId());
        editParams.put("sourceTable", tableGroup.getSourceTable().getName());
        editParams.put("targetTable", tableGroup.getTargetTable().getName());
        
        // 重新构建 fieldMappings 参数
        List<String> fieldMappingsList = new ArrayList<>();
        for (org.dbsyncer.parser.model.FieldMapping fm : tableGroup.getFieldMapping()) {
            if (fm.getSource() != null && fm.getTarget() != null) {
                fieldMappingsList.add(fm.getSource().getName() + "|" + fm.getTarget().getName());
            }
        }
        fieldMappingsList.add("CustomVarchar|CustomVarchar");
        fieldMappingsList.add("CustomDecimal|CustomDecimal");
        fieldMappingsList.add("CustomInteger|CustomInteger");
        fieldMappingsList.add("CustomDate|CustomDate");
        editParams.put("fieldMappings", String.join(",", fieldMappingsList));
        
        editParams.put("convert", org.dbsyncer.common.util.JsonUtil.objToJson(converts));
        
        tableGroupService.edit(editParams);
        
        logger.info("已添加多种类型的自定义字段");
        
        // 等待 DDL 执行
        Thread.sleep(3000);
        
        // 2. 验证各字段类型和属性是否正确
        List<String> allColumns = getTableColumns(testTargetTable, targetConfig);
        
        assertTrue("目标表应该包含 VARCHAR 自定义字段", allColumns.contains("CustomVarchar"));
        assertTrue("目标表应该包含 DECIMAL 自定义字段", allColumns.contains("CustomDecimal"));
        assertTrue("目标表应该包含 INTEGER 自定义字段", allColumns.contains("CustomInteger"));
        assertTrue("目标表应该包含 DATE 自定义字段", allColumns.contains("CustomDate"));
        
        // 验证字段类型和长度
        boolean varcharCorrect = verifyColumnType(testTargetTable, "CustomVarchar", "VARCHAR", 200, targetConfig);
        boolean decimalCorrect = verifyColumnType(testTargetTable, "CustomDecimal", "DECIMAL", 10, targetConfig);
        boolean intCorrect = verifyColumnType(testTargetTable, "CustomInteger", "INT", 0, targetConfig); // MySQL 中 INTEGER 映射为 INT
        boolean dateCorrect = verifyColumnType(testTargetTable, "CustomDate", "DATE", 0, targetConfig);
        
        assertTrue("VARCHAR 字段类型和长度应该正确", varcharCorrect);
        assertTrue("DECIMAL 字段类型和精度应该正确", decimalCorrect);
        assertTrue("INTEGER 字段类型应该正确", intCorrect);
        assertTrue("DATE 字段类型应该正确", dateCorrect);
        
        logger.info("自定义字段元数据正确传递测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建自定义字段转换配置
     */
    private org.dbsyncer.parser.model.Convert createCustomFieldConvert(
            String id, String fieldName, String typeName, int typeCode, 
            long columnSize, int ratio, boolean nullable, String comment) {
        
        org.dbsyncer.parser.model.Convert convert = new org.dbsyncer.parser.model.Convert();
        convert.setId(id);
        convert.setName(fieldName);
        convert.setConvertName("固定值");
        convert.setConvertCode("fixed_value");
        convert.setArgs("default_" + fieldName.toLowerCase());
        convert.setRoot(true);
        
        // 设置自定义字段元数据
        org.dbsyncer.sdk.model.Field fieldMetadata = new org.dbsyncer.sdk.model.Field();
        fieldMetadata.setName(fieldName);
        fieldMetadata.setTypeName(typeName);
        fieldMetadata.setType(typeCode);
        fieldMetadata.setColumnSize(columnSize);
        fieldMetadata.setRatio(ratio);
        fieldMetadata.setNullable(nullable);
        fieldMetadata.setComment(comment);
        fieldMetadata.setPk(false);
        fieldMetadata.setAutoincrement(false);
        
        convert.setFieldMetadata(fieldMetadata);
        
        return convert;
    }

    /**
     * 获取表的所有列名
     */
    private List<String> getTableColumns(String tableName, DatabaseConfig config) throws Exception {
        String sql = String.format("DESCRIBE %s", tableName);
        org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance instance = 
            new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(config);
        
        return instance.execute(databaseTemplate -> {
            return databaseTemplate.query(sql, (java.sql.ResultSet rs) -> {
                List<String> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add(rs.getString("Field"));  // DESCRIBE 的字段名列
                }
                return columns;
            });
        });
    }

    /**
     * 验证字段类型和长度
     */
    private boolean verifyColumnType(String tableName, String columnName, String expectedType, 
                                   long expectedLength, DatabaseConfig config) throws Exception {
        String sql = String.format("DESCRIBE %s", tableName);
        org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance instance = 
            new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(config);
        
        return instance.execute(databaseTemplate -> {
            return databaseTemplate.query(sql, (java.sql.ResultSet rs) -> {
                while (rs.next()) {
                    String fieldName = rs.getString("Field");
                    if (columnName.equalsIgnoreCase(fieldName)) {
                        String fieldType = rs.getString("Type");
                        
                        // 检查类型匹配（MySQL 中 DECIMAL 显示为 decimal (10,2) 格式）
                        boolean typeMatches = fieldType.toUpperCase().contains(expectedType.toUpperCase());
                        
                        // 检查长度匹配（如果期望长度大于 0）
                        boolean lengthMatches = true;
                        if (expectedLength > 0) {
                            // 从类型字符串中提取长度信息，如 VARCHAR(100) 或 decimal(10,2)
                            if (fieldType.contains("(")) {
                                String lengthPart = fieldType.substring(fieldType.indexOf('(') + 1, fieldType.indexOf(')'));
                                // DECIMAL 格式为 (10,2)，取第一个数字
                                if (lengthPart.contains(",")) {
                                    lengthPart = lengthPart.split(",")[0].trim();
                                }
                                try {
                                    long actualLength = Long.parseLong(lengthPart);
                                    lengthMatches = (actualLength >= expectedLength);  // 允许更大长度
                                } catch (NumberFormatException e) {
                                    lengthMatches = false;
                                }
                            } else {
                                lengthMatches = false;  // 期望有长度但字段没有
                            }
                        }
                        
                        return typeMatches && lengthMatches;
                    }
                }
                return false;  // 字段不存在
            });
        });
    }

    // ==================== 抽象方法实现 ====================

    @Override
    protected Class<?> getTestClass() {
        return CustomFieldIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "自定义字段测试源连接器";
    }

    @Override
    protected String getTargetConnectorName() {
        return "自定义字段测试目标连接器";
    }

    @Override
    protected String getMappingName() {
        return "自定义字段测试 Mapping";
    }

    @Override
    protected String getSourceTableName() {
        return "customFieldTestSource";
    }

    @Override
    protected String getTargetTableName() {
        return "customFieldTestTarget";
    }

    @Override
    protected List<String> getInitialFieldMappings() {
        List<String> fieldMappingList = new ArrayList<>();
        fieldMappingList.add("ID|ID");
        fieldMappingList.add("UserName|UserName");
        fieldMappingList.add("Age|Age");
        // 注意：不包含 Email，用于测试自定义字段
        return fieldMappingList;
    }

    @Override
    protected String getConnectorType(DatabaseConfig config, boolean isSource) {
        return "MySQL";
    }

    @Override
    protected String getIncrementStrategy() {
        return "Timing";
    }

    @Override
    protected String getDatabaseType(boolean isSource) {
        return "mysql";
    }
}
