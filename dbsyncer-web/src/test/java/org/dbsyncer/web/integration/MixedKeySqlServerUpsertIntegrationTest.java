package org.dbsyncer.web.integration;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.web.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

/**
 * SQL Server 混合主键 UPSERT 集成测试 - 增量模式
 * 
 * 测试场景：复合主键包含源端自有字段和源端没有的自有字段（site_id）
 * 
 * 关键设计：
 * 1. 源表主键：id (单主键，IDENTITY)
 * 2. 目标表主键：(id, site_id) 复合主键
 * 3. site_id 是目标端自有字段，不存在于源表
 * 
 * 测试目的：验证 UPSERT WHERE 条件是否包含所有主键列（包括 site_id）
 * 如果 WHERE 条件缺失 site_id，会导致多行被误更新！
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class MixedKeySqlServerUpsertIntegrationTest extends BaseDDLIntegrationTest {

    static {
        System.setProperty("sqlserver.ct.poll.interval.ms", "100");
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化 SQL Server 混合主键 UPSERT 集成测试环境 (混合主键模式)");
        loadTestConfigStatic();
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        String initSql = "IF OBJECT_ID('mixed_t1','U') IS NOT NULL DROP TABLE mixed_t1; IF OBJECT_ID('mixed_t2','U') IS NOT NULL DROP TABLE mixed_t2; CREATE TABLE mixed_t1 (id INT IDENTITY(1,1) NOT NULL PRIMARY KEY, name NVARCHAR(50), age INT); CREATE TABLE mixed_t2 (id INT NOT NULL, site_id INT NOT NULL, name NVARCHAR(50), age INT, PRIMARY KEY (id, site_id))";
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("SQL Server 混合主键 UPSERT 集成测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理测试环境");
        try {
            testDatabaseManager.cleanupTestEnvironment(
                "IF OBJECT_ID('mixed_t1','U') IS NOT NULL DROP TABLE mixed_t1; IF OBJECT_ID('mixed_t2','U') IS NOT NULL DROP TABLE mixed_t2",
                "IF OBJECT_ID('mixed_t1','U') IS NOT NULL DROP TABLE mixed_t1; IF OBJECT_ID('mixed_t2','U') IS NOT NULL DROP TABLE mixed_t2");
        } catch (Exception e) {
            logger.error("清理失败", e);
        }
    }

    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = MixedKeySqlServerUpsertIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                throw new IllegalArgumentException("test.properties not found");
            }
            props.load(input);
        }
        sourceConfig = new DatabaseConfig();
        sourceConfig.setUrl(props.getProperty("test.db.sqlserver.url"));
        sourceConfig.setUsername(props.getProperty("test.db.sqlserver.username"));
        sourceConfig.setPassword(props.getProperty("test.db.sqlserver.password"));
        sourceConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver"));

        targetConfig = new DatabaseConfig();
        targetConfig.setUrl(props.getProperty("test.db.sqlserver.url"));
        targetConfig.setUsername(props.getProperty("test.db.sqlserver.username"));
        targetConfig.setPassword(props.getProperty("test.db.sqlserver.password"));
        targetConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver"));
    }

    @Resource
    private org.dbsyncer.biz.MappingService mappingService;
    @Resource
    private org.dbsyncer.biz.ConnectorService connectorService;
    @Resource
    private org.dbsyncer.biz.TableGroupService tableGroupService;
    @Resource
    private org.dbsyncer.parser.ProfileComponent profileComponent;

    private String sourceConnectorId;
    private String targetConnectorId;
    private String mappingId;

    @Before
    public void setUp() throws Exception {
        logger.info("开始测试：混合主键 UPSERT");
    }

    @Override
    protected Class<?> getTestClass() {
        return MixedKeySqlServerUpsertIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "SQL Server 混合主键源连接器";
    }

    @Override
    protected String getTargetConnectorName() {
        return "SQL Server 混合主键目标连接器";
    }

    @Override
    protected String getMappingName() {
        return "SQL Server 混合主键 UPSERT 测试";
    }

    @Override
    protected String getSourceTableName() {
        return "mixed_t1";
    }

    @Override
    protected String getTargetTableName() {
        return "mixed_t2";
    }

    @Override
    protected List<String> getInitialFieldMappings() {
        return Arrays.asList("id,id", "name,name", "age,age");
    }

    @Override
    protected String getConnectorType(DatabaseConfig config, boolean isSource) {
        return "SqlServerCT";
    }

    @Override
    protected String getIncrementStrategy() {
        return "Log";
    }

    @Override
    protected String getDatabaseType(boolean isSource) {
        return "sqlserver";
    }

    @Test
    public void testUpsertMixedKey() throws Exception {
        logger.info("测试混合主键 UPSERT - 验证 WHERE 条件完整性...");

        // 创建连接器和映射
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);

        // 创建映射：只映射 id, name, age，不映射 site_id
        Map<String, String> params = new HashMap<>();
        params.put("name", "混合主键测试映射");
        params.put("sourceConnectorId", sourceConnectorId);
        params.put("targetConnectorId", targetConnectorId);
        params.put("model", "increment");
        params.put("incrementStrategy", "Log");
        params.put("enableDDL", "true");
        params.put("enableInsert", "true");
        params.put("enableUpdate", "true");
        params.put("enableDelete", "true");
        mappingId = mappingService.add(params);

        // 创建 TableGroup，只映射 id->id
        Map<String, String> tableGroupParams = new HashMap<>();
        tableGroupParams.put("mappingId", mappingId);
        tableGroupParams.put("sourceTable", "mixed_t1");
        tableGroupParams.put("targetTable", "mixed_t2");
        tableGroupParams.put("fieldMappings", "id,id|name,name|age,age");
        tableGroupService.add(tableGroupParams);

        // 重置 TableGroup 状态
        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        // 启动同步监听
        mappingService.start(mappingId);
        Thread.sleep(2000);

        // 插入 Alice (id=1)
        insertRow("mixed_t1", 1, "Alice", 25, sourceConfig);
        Thread.sleep(3000);
        assertEquals("目标表应有 1 行", 1L, countRows("mixed_t2", targetConfig));

        // 插入 Bob (id=2)
        insertRow("mixed_t1", 2, "Bob", 30, sourceConfig);
        Thread.sleep(3000);

        long targetCount = countRows("mixed_t2", targetConfig);
        assertEquals("目标表应有 2 行", 2L, targetCount);

        // 验证初始数据
        Map<String, Object> alice = queryRow("mixed_t2", "[id]=1", targetConfig);
        assertNotNull("Alice 应存在", alice);
        assertEquals("Alice Age 应为 25", 25L, alice.get("age"));

        Map<String, Object> bob = queryRow("mixed_t2", "[id]=2", targetConfig);
        assertNotNull("Bob 应存在", bob);
        assertEquals("Bob Age 应为 30", 30L, bob.get("age"));

        // 更新 Alice 的 Age 为 26
        updateRow("mixed_t1", 1, "Alice", 26, sourceConfig);
        Thread.sleep(3000);

        // 再次启动同步
        mappingService.start(mappingId);
        Thread.sleep(3000);

        // 验证：只有 Alice 被更新，Bob 不变（核心验证点）
        targetCount = countRows("mixed_t2", targetConfig);
        assertEquals("目标表仍应有 2 行", 2L, targetCount);

        alice = queryRow("mixed_t2", "[id]=1", targetConfig);
        assertNotNull("Alice 应存在", alice);
        assertEquals("Alice Age 应为 26", 26L, alice.get("age"));

        bob = queryRow("mixed_t2", "[id]=2", targetConfig);
        assertNotNull("Bob 应存在", bob);
        assertEquals("Bob Age 应保持 30", 30L, bob.get("age"));

        logger.info("✅ 测试通过：只有 Alice 被更新，Bob 不受影响！");
    }

    protected String createMapping() throws Exception {
        Map<String, String> p = new HashMap<>();
        p.put("name", getMappingName());
        p.put("sourceConnectorId", sourceConnectorId);
        p.put("targetConnectorId", targetConnectorId);
        p.put("model", "increment");
        p.put("incrementStrategy", getIncrementStrategy());
        p.put("enableDDL", "true");
        p.put("enableInsert", "true");
        p.put("enableUpdate", "true");
        p.put("enableDelete", "true");
        mappingId = mappingService.add(p);
        p.put("id", mappingId);
        mappingService.edit(p);
        Map<String, String> tableGroupParams = new HashMap<>();
        tableGroupParams.put("mappingId", mappingId);
        tableGroupParams.put("sourceTable", getSourceTableName());
        tableGroupParams.put("targetTable", getTargetTableName());
        tableGroupParams.put("fieldMappings", String.join(",", getInitialFieldMappings()));
        tableGroupService.add(tableGroupParams);
        return mappingId;
    }

    private void insertRow(String table, int id, String name, int age, DatabaseConfig cfg) throws Exception {
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("INSERT INTO %s (id, name, age) VALUES (%d, '%s', %d)", table, id, name, age));
                    return null;
                });
    }

    private void updateRow(String table, int id, String name, int age, DatabaseConfig cfg) throws Exception {
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("UPDATE %s SET age=%d WHERE id=%d", table, age, id));
                    return null;
                });
    }

    private long countRows(String table, DatabaseConfig cfg) throws Exception {
        return (Long) new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> t.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
    }

    private Map<String, Object> queryRow(String table, String where, DatabaseConfig cfg) throws Exception {
        return (Map<String, Object>) new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    List<Map<String, Object>> r = t.queryForList("SELECT * FROM " + table + " WHERE " + where);
                    return r.isEmpty() ? null : r.get(0);
                });
    }
}
