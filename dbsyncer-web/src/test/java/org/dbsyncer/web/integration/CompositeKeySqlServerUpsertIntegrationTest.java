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
 * SQL Server 复合主键 UPSERT 集成测试 - 增量模式
 * 测试联合主键 (id1, id2) 场景下 UPSERT 的 WHERE 条件正确性
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class CompositeKeySqlServerUpsertIntegrationTest extends BaseDDLIntegrationTest {

    static {
        System.setProperty("sqlserver.ct.poll.interval.ms", "100");
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化 SQL Server 复合主键 UPSERT 集成测试环境 (增量模式)");
        loadTestConfigStatic();
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        String initSql = "IF OBJECT_ID('t1','U') IS NOT NULL DROP TABLE t1; IF OBJECT_ID('t2','U') IS NOT NULL DROP TABLE t2; CREATE TABLE t1 (id1 INT NOT NULL, id2 INT NOT NULL, name NVARCHAR(50), age INT, PRIMARY KEY (id1, id2)); CREATE TABLE t2 (id1 INT NOT NULL, id2 INT NOT NULL, name NVARCHAR(50), age INT, PRIMARY KEY (id1, id2))";
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("SQL Server 复合主键 UPSERT 集成测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理测试环境");
        try {
            testDatabaseManager.cleanupTestEnvironment("IF OBJECT_ID('t1','U') IS NOT NULL DROP TABLE t1; IF OBJECT_ID('t2','U') IS NOT NULL DROP TABLE t2", "IF OBJECT_ID('t1','U') IS NOT NULL DROP TABLE t1; IF OBJECT_ID('t2','U') IS NOT NULL DROP TABLE t2");
        } catch (Exception e) {
            logger.error("清理失败", e);
        }
    }

    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = CompositeKeySqlServerUpsertIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
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
        logger.info("开始测试：复合主键 UPSERT");
    }

    @Override
    protected Class<?> getTestClass() {
        return CompositeKeySqlServerUpsertIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "SQL Server 复合主键源连接器";
    }

    @Override
    protected String getTargetConnectorName() {
        return "SQL Server 复合主键目标连接器";
    }

    @Override
    protected String getMappingName() {
        return "SQL Server 复合主键 UPSERT 测试";
    }

    @Override
    protected String getSourceTableName() {
        return "t1";
    }

    @Override
    protected String getTargetTableName() {
        return "t2";
    }

    @Override
    protected List<String> getInitialFieldMappings() {
        return Arrays.asList("id1,id1", "id2,id2", "name,name", "age,age");
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
    public void testUpsertCompositeKey() throws Exception {
        logger.info("测试复合主键 UPSERT (增量模式)...");

        // 创建连接器和映射
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);
        mappingId = createMapping();

        // 重置 TableGroup 状态
        List<org.dbsyncer.parser.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.parser.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        // 启动同步监听
        mappingService.start(mappingId);
        Thread.sleep(2000);

        // 插入 Alice 和 Bob
        insertRow("t1", 1, 1, "Alice", 25, sourceConfig);
        insertRow("t1", 1, 2, "Bob", 30, sourceConfig);
        Thread.sleep(3000);

        long targetCount = countRows("t2", targetConfig);
        assertEquals("目标表应有 2 行", 2L, targetCount);

        // 更新 Alice 的 Age
        updateRow("t1", 1, 1, "Alice", 26, sourceConfig);
        Thread.sleep(3000);

        // 验证：Alice 更新，Bob 不变
        Map<String, Object> alice = queryRow("t2", "[id1]=1 AND [id2]=1", targetConfig);
        Map<String, Object> bob = queryRow("t2", "[id1]=1 AND [id2]=2", targetConfig);

        assertNotNull("Alice 应存在", alice);
        assertNotNull("Bob 应存在", bob);
        assertEquals("Alice Age 应为 26", 26, alice.get("age"));
        assertEquals("Bob Age 应保持 30", 30, bob.get("age"));

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

    private void insertRow(String table, int id1, int id2, String name, int age, DatabaseConfig cfg) throws Exception {
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("INSERT INTO %s (id1, id2, name, age) VALUES (%d, %d, '%s', %d)", table, id1, id2, name, age));
                    return null;
                });
    }

    private void updateRow(String table, int id1, int id2, String name, int age, DatabaseConfig cfg) throws Exception {
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("UPDATE %s SET age=%d WHERE id1=%d AND id2=%d", table, age, id1, id2));
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
