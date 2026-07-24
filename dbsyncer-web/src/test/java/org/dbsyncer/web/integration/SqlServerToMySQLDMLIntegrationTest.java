package org.dbsyncer.web.integration;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.web.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;

/**
 * SQL Server 2008 R2 到 MySQL 的 DML 集成测试
 * 测试 SQL Server 2008 R2 所有主要数据类型的同步
 * 
 * 测试数据类型（34 个字段，符合 SQL Server 标识符长度限制）:
 * 【数值类型】10 个：BIGINT, INT, SMALLINT, TINYINT, DECIMAL(18,2), NUMERIC(18,4), FLOAT, REAL, MONEY, SMALLMONEY
 * 【布尔类型】1 个：BIT
 * 【字符类型】6 个：CHAR(16), VARCHAR(128), NCHAR(16), NVARCHAR(128), TEXT, NTEXT
 * 【日期时间】6 个：DATE, TIME(6), DATETIME, DATETIME2(7), SMALLDATETIME, DATETIMEOFFSET
 * 【二进制】3 个：BINARY(16), VARBINARY(128), IMAGE
 * 【其他】2 个：UNIQUEIDENTIFIER, XML
 * 【辅助字段】4 个：ID, BN, U, A, ST, AM
 * 
 * 测试场景：
 * - INSERT 全量同步
 * - UPDATE 全量同步
 * - UPDATE 增量同步
 * - DELETE 增量同步
 * - UPSERT 操作
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class SqlServerToMySQLDMLIntegrationTest extends BaseDDLIntegrationTest {

    static {
        System.setProperty("sqlserver.ct.poll.interval.ms", "100");
    }

    private static DatabaseConfig sqlServerConfig;
    private static DatabaseConfig mysqlConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化 SQL Server 2008 R2 到 MySQL 的 DML 集成测试环境");
        loadTestConfigStatic();
        sourceConfig = sqlServerConfig;
        targetConfig = mysqlConfig;
        testDatabaseManager = new TestDatabaseManager(sqlServerConfig, mysqlConfig);
        logger.info("SQL Server 2008 R2 到 MySQL 的 DML 集成测试环境配置完成");
    }

    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = SqlServerToMySQLDMLIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                logger.warn("未找到 test.properties 配置文件，使用默认配置");
                sqlServerConfig = createDefaultSQLServerConfig();
                mysqlConfig = createDefaultMySQLConfig();
                return;
            }
            props.load(input);
        }
        sqlServerConfig = new DatabaseConfig();
        sqlServerConfig.setUrl(props.getProperty("test.db.sqlserver.url", "jdbc:sqlserver://127.0.0.1:1433;DatabaseName=source_db;encrypt=false;trustServerCertificate=true"));
        sqlServerConfig.setUsername(props.getProperty("test.db.sqlserver.username", "sa"));
        sqlServerConfig.setPassword(props.getProperty("test.db.sqlserver.password", "123456"));
        sqlServerConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
        mysqlConfig = new DatabaseConfig();
        mysqlConfig.setUrl(props.getProperty("test.db.mysql.url", "jdbc:mysql://127.0.0.1:3306/target_db"));
        mysqlConfig.setUsername(props.getProperty("test.db.mysql.username", "root"));
        mysqlConfig.setPassword(props.getProperty("test.db.mysql.password", "123456"));
        mysqlConfig.setDriverClassName(props.getProperty("test.db.mysql.driver", "com.mysql.cj.jdbc.Driver"));
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理 SQL Server 2008 R2 到 MySQL 的 DML 集成测试环境");
        try {
            String sourceCleanupSql = loadSqlScriptByDatabaseTypeStatic("cleanup-test-data", "sqlserver", SqlServerToMySQLDMLIntegrationTest.class);
            String targetCleanupSql = loadSqlScriptByDatabaseTypeStatic("cleanup-test-data", "mysql", SqlServerToMySQLDMLIntegrationTest.class);
            testDatabaseManager.cleanupTestEnvironment(sourceCleanupSql, targetCleanupSql);
            logger.info("SQL Server 2008 R2 到 MySQL 的 DML 集成测试环境清理完成");
        } catch (Exception e) {
            logger.error("清理测试环境失败", e);
        }
    }

    @Before
    public void setUp() throws Exception {
        cleanupResidualTestMappings();
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);
        resetDatabaseTableStructure();
        mappingId = createMapping();
        metaId = profileComponent.getMapping(mappingId).getMetaId();
        logger.info("SQL Server 2008 R2 到 MySQL 的 DML 集成测试用例环境初始化完成");
    }

    @Override
    protected void resetDatabaseTableStructure() {
        logger.debug("开始重置测试数据库表结构 - 包含所有 SQL Server 2008 R2 数据类型");
        try {
            String testSourceTable = getSourceTableName();
            String testTargetTable = getTargetTableName();
            String dropSourceSql = String.format("IF OBJECT_ID('%s', 'U') IS NOT NULL DROP TABLE %s", testSourceTable, testSourceTable);
            String createSourceTableDDL = createSourceTableDDL(testSourceTable);
            String dropTargetSql = String.format("DROP TABLE IF EXISTS %s", testTargetTable);
            String createTargetTableDDL = createTargetTableDDL(testTargetTable);
            executeDDLToSourceDatabase(dropSourceSql, sourceConfig);
            executeDDLToSourceDatabase(createSourceTableDDL, sourceConfig);
            executeDDLToSourceDatabase(dropTargetSql, targetConfig);
            executeDDLToSourceDatabase(createTargetTableDDL, targetConfig);
            logger.debug("测试数据库表结构重置完成");
        } catch (Exception e) {
            logger.error("重置测试数据库表结构失败", e);
        }
    }

    /**
     * 执行 DDL 到目标数据库
     */
    protected void executeDDLToTargetDatabase(String sql, DatabaseConfig config) throws Exception {
        DatabaseConnectorInstance instance = new DatabaseConnectorInstance(config);
        instance.execute(databaseTemplate -> {
            databaseTemplate.execute(sql);
            return null;
        });
        logger.info("在目标库执行了 sql: {}", sql);
    }

    private String createSourceTableDDL(String tableName) {
        return String.format(
            "CREATE TABLE %s (\n" +
            "    [ID] BIGINT IDENTITY(1,1) NOT NULL,\n" +
            "    [BN] NVARCHAR(64) NOT NULL,\n" +
            "    [U] NVARCHAR(50) NOT NULL,\n" +
            "    [A] INT NOT NULL,\n" +
            "    [ST] INT NULL,\n" +
            "    [TI] TINYINT NULL,\n" +
            "    [SI] SMALLINT NULL,\n" +
            "    [I] INT NULL,\n" +
            "    [BI] BIGINT NULL,\n" +
            "    [DE] DECIMAL(18,2) NULL,\n" +
            "    [NU] NUMERIC(18,4) NULL,\n" +
            "    [FL] FLOAT NULL,\n" +
            "    [RE] REAL NULL,\n" +
            "    [MO] MONEY NULL,\n" +
            "    [SM] SMALLMONEY NULL,\n" +
            "    [BT] BIT NULL,\n" +
            "    [C] CHAR(16) NULL,\n" +
            "    [V] VARCHAR(128) NULL,\n" +
            "    [T] TEXT NULL,\n" +
            "    [NC] NCHAR(16) NULL,\n" +
            "    [NV] NVARCHAR(128) NULL,\n" +
            "    [NT] NTEXT NULL,\n" +
            "    [DA] DATE NULL,\n" +
            "    [TM] TIME(6) NULL,\n" +
            "    [DT] DATETIME NULL,\n" +
            "    [D2] DATETIME2(7) NULL,\n" +
            "    [SD] SMALLDATETIME NULL,\n" +
            "    [DOF] DATETIMEOFFSET(6) NULL,\n" +
            "    [BIN] BINARY(16) NULL,\n" +
            "    [VBIN] VARBINARY(128) NULL,\n" +
            "    [IMG] IMAGE NULL,\n" +
            "    [GUID] UNIQUEIDENTIFIER NULL,\n" +
            "    [XM] XML NULL,\n" +
            "    [AM] DECIMAL(18,2) NULL,\n" +
            "    PRIMARY KEY ([ID])\n" +
            ")", tableName);
    }

    private String createTargetTableDDL(String tableName) {
        return String.format(
            "CREATE TABLE %s (\n" +
            "    ID BIGINT NOT NULL,\n" +
            "    BN VARCHAR(64) NOT NULL,\n" +
            "    U VARCHAR(50) NOT NULL,\n" +
            "    A INT NOT NULL,\n" +
            "    ST INT DEFAULT 0,\n" +
            "    TI TINYINT DEFAULT 0,\n" +
            "    SI SMALLINT DEFAULT 0,\n" +
            "    I INT DEFAULT 0,\n" +
            "    BI BIGINT DEFAULT 0,\n" +
            "    DE DECIMAL(18,2) DEFAULT 0.00,\n" +
            "    NU DECIMAL(18,4) DEFAULT 0.0000,\n" +
            "    FL DOUBLE DEFAULT 0.0,\n" +
            "    RE FLOAT DEFAULT 0.0,\n" +
            "    MO DECIMAL(19,4) DEFAULT 0.0000,\n" +
            "    SM DECIMAL(10,4) DEFAULT 0.0000,\n" +
            "    BT TINYINT DEFAULT 0,\n" +
            "    C CHAR(16) DEFAULT '',\n" +
            "    V VARCHAR(128) DEFAULT '',\n" +
            "    T LONGTEXT,\n" +
            "    NC CHAR(16) DEFAULT '',\n" +
            "    NV VARCHAR(128) DEFAULT '',\n" +
            "    NT MEDIUMTEXT,\n" +
            "    DA DATE DEFAULT NULL,\n" +
            "    TM TIME(6) DEFAULT '00:00:00.000000',\n" +
            "    DT DATETIME DEFAULT '1970-01-01 00:00:00',\n" +
            "    D2 DATETIME(6) DEFAULT '1970-01-01 00:00:00.000000',\n" +
            "    SD DATETIME DEFAULT '1970-01-01 00:00:00',\n" +
            "    DOF VARCHAR(40) DEFAULT NULL,\n" +
            "    BIN BINARY(16) DEFAULT NULL,\n" +
            "    VBIN VARBINARY(128) DEFAULT NULL,\n" +
            "    IMG LONGBLOB,\n" +
            "    GUID CHAR(36) DEFAULT NULL,\n" +
            "    XM LONGTEXT,\n" +
            "    AM DECIMAL(18,2) DEFAULT 0.00,\n" +
            "    PRIMARY KEY (ID),\n" +
            "    UNIQUE KEY UK_BN (BN)\n" +
            ")", tableName);
    }

    @After
    public void tearDown() {
        try {
            if (mappingId != null) {
                try { mappingService.stop(mappingId); Thread.sleep(500); } catch (Exception e) {}
                try { mappingService.remove(mappingId); } catch (Exception e) {}
            }
        } catch (Exception e) {}

        try {
            if (sourceConnectorId != null) {
                try { connectorService.remove(sourceConnectorId); } catch (Exception e) {}
            }
            if (targetConnectorId != null) {
                try { connectorService.remove(targetConnectorId); } catch (Exception e) {}
            }
        } catch (Exception e) {}
    }

    // ==================== INSERT 测试场景 ====================

    @Test
    public void testInsert_FullSync() throws Exception {
        logger.info("开始测试 INSERT 操作 - SQL Server 2008 R2 到 MySQL 全量同步插入所有数据类型");

        String testSourceTable = getSourceTableName();
        String testTargetTable = getTargetTableName();

        // 1. 重置 TableGroup 完成状态
        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        // 2. 配置 Mapping
        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "full");
        editParams.put("incrementStrategy", "Log");
        editParams.put("forceUpdate", "false");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);

        // 3. 在源表插入 34 字段数据
        Map<String, Object> sourceData = createTestData();
        sourceData = executeInsertDMLToSourceDatabase(testSourceTable, sourceData, sourceConfig);
        logger.info("源表已插入所有数据类型数据，ID={}", sourceData.get("ID"));

        // 4. 启动全量同步
        mappingService.start(mappingId);
        Thread.sleep(5000);

        // 5. 验证数据同步
        Map<String, Object> targetData = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertNotNull("目标表中应该找到数据", targetData);
        
        // 验证所有字段
        assertEquals("DECIMAL 应该同步", sourceData.get("DE"), targetData.get("DE"));
        assertEquals("NUMERIC 应该同步", sourceData.get("NU"), targetData.get("NU"));
        assertEquals("FLOAT 应该同步", sourceData.get("FL"), targetData.get("FL"));
        float expectedRE = ((Float) sourceData.get("RE")).floatValue();
        float actualRE = ((Float) targetData.get("RE")).floatValue();
        assertEquals("REAL 应该同步（精度范围）", expectedRE, actualRE, 0.0001f);
        assertEquals("MONEY 应该同步", sourceData.get("MO"), targetData.get("MO"));
        assertEquals("SMALLMONEY 应该同步", sourceData.get("SM"), targetData.get("SM"));
        assertEquals("BIGINT(BI) 应该同步", sourceData.get("BI"), targetData.get("BI"));
        assertEquals("INT 应该同步", sourceData.get("A"), targetData.get("A"));
        assertEquals("SMALLINT 应该同步", ((Number)sourceData.get("SI")).intValue(), ((Number)targetData.get("SI")).intValue());
        assertEquals("TINYINT 应该同步", ((Number)sourceData.get("TI")).intValue(), ((Number)targetData.get("TI")).intValue());
        assertEquals("BIT 应该同步", sourceData.get("BT"), targetData.get("BT"));
        assertEquals("CHAR 应该同步", sourceData.get("C"), targetData.get("C"));
        assertEquals("VARCHAR 应该同步", sourceData.get("V"), targetData.get("V"));
        assertEquals("NCHAR 应该同步", sourceData.get("NC"), targetData.get("NC"));
        assertEquals("NVARCHAR 应该同步", sourceData.get("NV"), targetData.get("NV"));
        assertEquals("TEXT 应该同步", sourceData.get("T"), targetData.get("T"));
        assertEquals("NTEXT 应该同步", sourceData.get("NT"), targetData.get("NT"));
        assertEquals("DATE 应该同步", sourceData.get("DA").toString(), targetData.get("DA").toString());
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        assertEquals("TIME 应该同步", sdf.format((java.util.Date)sourceData.get("TM")), sdf.format((java.util.Date)targetData.get("TM")));
        long expectedDTMillis = ((java.util.Date)sourceData.get("DT")).getTime() / 1000 * 1000;
        long actualDTMillis = ((java.util.Date)targetData.get("DT")).getTime() / 1000 * 1000;
        assertEquals("DATETIME 应该同步（秒级精度）", expectedDTMillis, actualDTMillis);
        long expectedD2Millis = ((java.util.Date)sourceData.get("D2")).getTime() / 1000 * 1000;
        long actualD2Millis = ((java.util.Date)targetData.get("D2")).getTime() / 1000 * 1000;
        assertEquals("DATETIME2 应该同步（秒级精度）", expectedD2Millis, actualD2Millis);
        long expectedSDMillis = ((java.util.Date)sourceData.get("SD")).getTime() / 1000 * 1000;
        long actualSDMillis = ((java.util.Date)targetData.get("SD")).getTime() / 1000 * 1000;
        if (Math.abs(expectedSDMillis - actualSDMillis) > 60000) {
            fail(String.format("SMALLDATETIME 应该同步（期望=%d, 实际=%d, 差异=%dms）", expectedSDMillis, actualSDMillis, Math.abs(expectedSDMillis - actualSDMillis)));
        }
        String expectedDOF = sourceData.get("DOF").toString();
        String actualDOF = targetData.get("DOF").toString();
        String expectedDOFTime = expectedDOF.split("[+\\-=]")[0].trim();
        String actualDOFTime = actualDOF.split("[+\\-=]")[0].trim();
        assertEquals("DATETIMEOFFSET 应该同步（去掉时区）", expectedDOFTime, actualDOFTime);
        if (sourceData.get("BIN") != null && targetData.get("BIN") != null) {
            assertArrayEquals("BINARY 应该同步", (byte[])sourceData.get("BIN"), (byte[])targetData.get("BIN"));
        }
        if (sourceData.get("VBIN") != null && targetData.get("VBIN") != null) {
            assertArrayEquals("VARBINARY 应该同步", (byte[])sourceData.get("VBIN"), (byte[])targetData.get("VBIN"));
        }
        if (sourceData.get("IMG") != null && targetData.get("IMG") != null) {
            assertArrayEquals("IMAGE 应该同步", (byte[])sourceData.get("IMG"), (byte[])targetData.get("IMG"));
        }
        assertEquals("UNIQUEIDENTIFIER 应该同步", sourceData.get("GUID").toString().toUpperCase(), targetData.get("GUID").toString().toUpperCase());
        assertEquals("XML 应该同步", sourceData.get("XM"), targetData.get("XM"));

        logger.info("INSERT SQL Server 2008 R2 到 MySQL 全量同步测试通过");
    }

    // ==================== UPDATE 测试场景 ====================

    @Test
    public void testUpdate_ExistingData_full() throws Exception {
        logger.info("开始测试 UPDATE 操作 - SQL Server 2008 R2 到 MySQL 更新已存在的数据（全量）");

        String testSourceTable = getSourceTableName();
        String testTargetTable = getTargetTableName();

        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "full");
        editParams.put("incrementStrategy", "Log");
        editParams.put("forceUpdate", "true");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);

        Map<String, Object> sourceData = createTestData();
        sourceData = executeInsertDMLToSourceDatabase(testSourceTable, sourceData, sourceConfig);
        logger.info("源表已插入初始数据，ID={}", sourceData.get("ID"));

        mappingService.start(mappingId);
        Thread.sleep(3000);

        // 更新部分字段
        DatabaseConnectorInstance sourceInstance = new DatabaseConnectorInstance(sourceConfig);
        String updateSql = String.format(
            "UPDATE %s SET U = '%s', A = %d, ST = %d WHERE ID = %d",
            testSourceTable, "UpdatedUser", 999, 99, sourceData.get("ID"));
        sourceInstance.execute(databaseTemplate -> {
            databaseTemplate.execute(updateSql);
            return null;
        });
        logger.info("源表已更新数据，ID={}, 新U=UpdatedUser, 新A=999", sourceData.get("ID"));

        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);
        mappingService.start(mappingId);
        Thread.sleep(3000);

        Map<String, Object> targetData = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertNotNull("目标表中应该找到数据", targetData);
        assertEquals("U 应该被更新", "UpdatedUser", targetData.get("U"));
        assertEquals("A 应该被更新", 999, targetData.get("A"));
        assertEquals("ST 应该被更新", 99, targetData.get("ST"));

        logger.info("UPDATE SQL Server 2008 R2 到 MySQL 全量同步测试通过");
    }

    @Test
    public void testUpdate_ExistingData_increment() throws Exception {
        logger.info("开始测试 UPDATE 操作 - SQL Server 2008 R2 到 MySQL 更新已存在的数据（增量）");

        String testSourceTable = getSourceTableName();
        String testTargetTable = getTargetTableName();

        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "increment");
        editParams.put("incrementStrategy", "Log");
        editParams.put("forceUpdate", "true");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);

        Map<String, Object> sourceData = createTestData();
        sourceData = executeInsertDMLToSourceDatabase(testSourceTable, sourceData, sourceConfig);
        logger.info("源表已插入初始数据，ID={}", sourceData.get("ID"));

        mappingService.start(mappingId);
        Thread.sleep(3000);

        DatabaseConnectorInstance sourceInstance = new DatabaseConnectorInstance(sourceConfig);
        String updateSql = String.format(
            "UPDATE %s SET U = '%s', A = %d, ST = %d WHERE ID = %d",
            testSourceTable, "IncrementUpdateUser", 888, 88, sourceData.get("ID"));
        sourceInstance.execute(databaseTemplate -> {
            databaseTemplate.execute(updateSql);
            return null;
        });
        logger.info("源表已更新数据，ID={}, 新U=IncrementUpdateUser, 新A=888", sourceData.get("ID"));

        Thread.sleep(3000);

        Map<String, Object> targetData = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertNotNull("目标表中应该找到数据", targetData);
        assertEquals("U 应该被更新", "IncrementUpdateUser", targetData.get("U"));
        assertEquals("A 应该被更新", 888, targetData.get("A"));
        assertEquals("ST 应该被更新", 88, targetData.get("ST"));

        logger.info("UPDATE SQL Server 2008 R2 到 MySQL 增量同步测试通过");
    }

    // ==================== DELETE 测试场景 ====================

    @Test
    public void testDelete_RemoveData() throws Exception {
        logger.info("开始测试 DELETE 操作 - SQL Server 2008 R2 到 MySQL 删除数据");

        String testSourceTable = getSourceTableName();
        String testTargetTable = getTargetTableName();

        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "increment");
        editParams.put("incrementStrategy", "Log");
        editParams.put("forceUpdate", "false");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);

        mappingService.start(mappingId);
        Thread.sleep(2000);

        Map<String, Object> sourceData = createTestData();
        sourceData = executeInsertDMLToSourceDatabase(testSourceTable, sourceData, sourceConfig);
        logger.info("源表已插入数据，ID={}, BN={}", sourceData.get("ID"), sourceData.get("BN"));

        Thread.sleep(2000);

        Map<String, Object> targetDataBeforeDelete = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertNotNull("删除前目标表中应该找到数据", targetDataBeforeDelete);

        DatabaseConnectorInstance sourceInstance = new DatabaseConnectorInstance(sourceConfig);
        String deleteSql = String.format("DELETE FROM %s WHERE ID = %d", testSourceTable, sourceData.get("ID"));
        sourceInstance.execute(databaseTemplate -> {
            databaseTemplate.execute(deleteSql);
            return null;
        });
        logger.info("源表已删除数据，ID={}", sourceData.get("ID"));

        long startTime = System.currentTimeMillis();
        long timeoutMs = 10000;
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Map<String, Object> checkData = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
            if (checkData.isEmpty()) {
                logger.info("目标表中数据已被删除，等待时间: {}ms", System.currentTimeMillis() - startTime);
                break;
            }
            Thread.sleep(300);
        }

        Map<String, Object> targetDataAfterDelete = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertTrue("目标表中数据应该被删除（查询结果应为空）", targetDataAfterDelete.isEmpty());

        logger.info("DELETE SQL Server 2008 R2 到 MySQL 操作测试通过");
    }

    // ==================== UPSERT 测试场景 ====================

    @Test
    public void testUpsert_IdentityToAutoIncrement() throws Exception {
        logger.info("开始测试 UPSERT 操作 - SQL Server 2008 R2 IDENTITY 列到 MySQL AUTO_INCREMENT");

        String testSourceTable = getSourceTableName();
        String testTargetTable = getTargetTableName();

        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "full");
        editParams.put("incrementStrategy", "Log");
        editParams.put("forceUpdate", "true");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);

        Map<String, Object> sourceData = createTestData();
        sourceData = executeInsertDMLToSourceDatabase(testSourceTable, sourceData, sourceConfig);
        logger.info("源表已插入数据，ID={}, BN={}", sourceData.get("ID"), sourceData.get("BN"));

        try {
            mappingService.start(mappingId);
            Thread.sleep(3000);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("无法更新标识列") || errorMsg.contains("Cannot update identity column"))) {
                fail("UPSERT 操作不应该尝试更新标识列: " + errorMsg);
            }
            throw e;
        }

        Map<String, Object> targetDataAfterSync = queryTableDataByPrimaryKey(testTargetTable, "BN", sourceData.get("BN"), targetConfig);
        assertNotNull("目标表中应该找到数据", targetDataAfterSync);
        assertEquals("ID 应该同步", ((Number)sourceData.get("ID")).longValue(), ((Number)targetDataAfterSync.get("ID")).longValue());
        assertEquals("U 应该同步", sourceData.get("U"), targetDataAfterSync.get("U"));
        assertEquals("A 应该同步", sourceData.get("A"), targetDataAfterSync.get("A"));

        logger.info("UPSERT SQL Server 2008 R2 到 MySQL 测试通过");
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> createTestData() {
        Map<String, Object> data = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        String bizNo = "TEST_BIZ_" + timestamp;
        data.put("ID", 1L);
        data.put("BN", bizNo);
        data.put("U", "测试用户_" + timestamp);
        data.put("A", 100);
        data.put("ST", 1);
        data.put("TI", (byte) 50);
        data.put("SI", (short) 500);
        data.put("I", 5000);
        data.put("BI", 50000L);
        data.put("DE", new BigDecimal("999999.99"));
        data.put("NU", new BigDecimal("999999.9999"));
        data.put("FL", 3.14159265358979);
        data.put("RE", 2.718281f);
        data.put("MO", new BigDecimal("9999999.9999"));
        data.put("SM", new BigDecimal("12345.6789"));
        data.put("BT", 1);
        data.put("C", "CHAR 测试");
        data.put("V", "VARCHAR 测试数据");
        data.put("T", "TEXT 测试数据");
        data.put("NC", "NCHAR 测试");
        data.put("NV", "NVARCHAR 测试数据");
        data.put("NT", "NTEXT 测试数据");
        data.put("DA", "2024-01-15");
        data.put("TM", new java.sql.Time(timestamp));
        data.put("DT", new java.sql.Timestamp(timestamp));
        data.put("D2", new java.sql.Timestamp(timestamp));
        data.put("SD", new java.sql.Timestamp(timestamp));
        data.put("DOF", createDateTimeOffset());
        data.put("BIN", "1234567890123456".getBytes());
        data.put("VBIN", "VARBINARY 测试数据".getBytes());
        data.put("IMG", "IMAGE 测试数据".getBytes());
        data.put("GUID", "550e8400-e29b-41d4-a716-446655440000");
        data.put("XM", "<root><test>XML 测试</test></root>");
        data.put("AM", new BigDecimal("1234.56"));
        return data;
    }

    private String createDateTimeOffset() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int offsetMillis = cal.getTimeZone().getOffset(cal.getTimeInMillis());
        int offsetHours = offsetMillis / 3600000;
        int offsetMinutes = Math.abs((offsetMillis % 3600000) / 60000);
        return String.format("%s%+03d:%02d",
            new java.sql.Timestamp(cal.getTimeInMillis()).toString().substring(0, 19),
            offsetHours, offsetMinutes);
    }

    private Map<String, Object> queryTableDataByPrimaryKey(String tableName, String primaryKeyColumn, Object primaryKeyValue, DatabaseConfig config) throws Exception {
        String whereCondition = primaryKeyColumn + " = " + (primaryKeyValue instanceof String ? "'" + primaryKeyValue + "'" : primaryKeyValue);
        String sql = String.format("SELECT * FROM %s WHERE %s", tableName, whereCondition);
        return queryTableData(sql, config);
    }

    private Map<String, Object> queryTableData(String sql, DatabaseConfig config) throws Exception {
        DatabaseConnectorInstance instance = new DatabaseConnectorInstance(config);
        return instance.execute(databaseTemplate -> {
            return databaseTemplate.query(sql, (java.sql.ResultSet rs) -> {
                Map<String, Object> data = new HashMap<>();
                if (rs.next()) {
                    java.sql.ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        data.put(columnName, value);
                    }
                }
                return data;
            });
        });
    }

    // ==================== 抽象方法实现 ====================

    @Override
    protected Class<?> getTestClass() { return SqlServerToMySQLDMLIntegrationTest.class; }

    @Override
    protected String getSourceConnectorName() { return "SQL Server 2008 R2 到 MySQL 源连接器"; }

    @Override
    protected String getTargetConnectorName() { return "SQL Server 2008 R2 到 MySQL 目标连接器"; }

    @Override
    protected String getMappingName() { return "SQL Server 2008 R2 到 MySQL DML 测试 Mapping"; }

    @Override
    protected String getSourceTableName() { return "ddlTestSource"; }

    @Override
    protected String getTargetTableName() { return "ddlTestTarget"; }

    @Override
    protected List<String> getInitialFieldMappings() {
        List<String> list = new ArrayList<>();
        list.add("ID|ID"); list.add("BN|BN"); list.add("U|U"); list.add("A|A"); list.add("ST|ST");
        list.add("TI|TI"); list.add("SI|SI"); list.add("I|I"); list.add("BI|BI"); list.add("DE|DE");
        list.add("NU|NU"); list.add("FL|FL"); list.add("RE|RE"); list.add("MO|MO"); list.add("SM|SM");
        list.add("BT|BT"); list.add("C|C"); list.add("V|V"); list.add("T|T"); list.add("NC|NC");
        list.add("NV|NV"); list.add("NT|NT"); list.add("DA|DA"); list.add("TM|TM"); list.add("DT|DT");
        list.add("D2|D2"); list.add("SD|SD"); list.add("DOF|DOF"); list.add("BIN|BIN"); list.add("VBIN|VBIN");
        list.add("IMG|IMG"); list.add("GUID|GUID"); list.add("XM|XM"); list.add("AM|AM");
        return list;
    }

    @Override
    protected String getConnectorType(DatabaseConfig config, boolean isSource) {
        return isSource ? "SqlServerCT" : "MySQL";
    }

    @Override
    protected String getIncrementStrategy() { return "Log"; }

    @Override
    protected String createMapping() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("name", getMappingName());
        params.put("sourceConnectorId", sourceConnectorId);
        params.put("targetConnectorId", targetConnectorId);
        params.put("model", "full");
        params.put("enableDDL", "true");
        params.put("enableInsert", "true");
        params.put("enableUpdate", "true");
        params.put("enableDelete", "true");
        String mappingId = mappingService.add(params);
        Map<String, String> editParams = new HashMap<>();
        editParams.put("id", mappingId);
        editParams.put("model", "full");
        editParams.put("incrementStrategy", "Log");
        editParams.put("enableDDL", "true");
        editParams.put("enableInsert", "true");
        editParams.put("enableUpdate", "true");
        editParams.put("enableDelete", "true");
        mappingService.edit(editParams);
        Map<String, String> tableGroupParams = new HashMap<>();
        tableGroupParams.put("mappingId", mappingId);
        tableGroupParams.put("sourceTable", getSourceTableName());
        tableGroupParams.put("targetTable", getTargetTableName());
        tableGroupParams.put("fieldMappings", String.join(",", getInitialFieldMappings()));
        tableGroupService.add(tableGroupParams);
        return mappingId;
    }

    @Override
    protected String getDatabaseType(boolean isSource) { return isSource ? "sqlserver" : "mysql"; }
}
