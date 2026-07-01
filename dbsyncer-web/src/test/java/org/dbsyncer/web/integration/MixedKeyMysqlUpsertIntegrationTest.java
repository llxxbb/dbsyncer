package org.dbsyncer.web.integration;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.model.Convert;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.FieldMapping;
import org.dbsyncer.sdk.model.TableGroup;
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
 * MySQL 混合主键 UPSERT 集成测试 - 增量模式
 * 
 * 测试场景：复合主键包含源端自有字段和源端没有的自有字段（site_id）
 * 
 * 关键设计：
 * 1. 源表主键：id (单主键)
 * 2. 目标表主键：(id, site_id) 复合主键
 * 3. site_id 是目标端自有字段，不存在于源表
 * 4. site_id 必须同时加入 fieldMapping（使 SQL 包含该列）和 FIXED 转换器（提供固定值 5）
 * 
 * 测试目的：验证 UPSERT WHERE 条件是否包含所有主键列（包括 site_id）
 * 如果 WHERE 条件缺失 site_id，会导致多行被误更新！
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class MixedKeyMysqlUpsertIntegrationTest extends BaseDDLIntegrationTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化 MySQL 混合主键 UPSERT 集成测试环境 (混合主键模式)");
        loadTestConfigStatic();
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        String initSql = "DROP TABLE IF EXISTS mixed_t1,mixed_t2; " +
                "CREATE TABLE mixed_t1 (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(50), age INT, PRIMARY KEY (id)); " +
                "CREATE TABLE mixed_t2 (id INT NOT NULL, site_id INT NOT NULL, name VARCHAR(50), age INT, PRIMARY KEY (id, site_id))";
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("MySQL 混合主键 UPSERT 集成测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理测试环境");
        try {
            testDatabaseManager.cleanupTestEnvironment("DROP TABLE IF EXISTS mixed_t1,mixed_t2", "DROP TABLE IF EXISTS mixed_t1,mixed_t2");
        } catch (Exception e) {
            logger.error("清理失败", e);
        }
    }

    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = MixedKeyMysqlUpsertIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                throw new IllegalArgumentException("test.properties not found");
            }
            props.load(input);
        }
        sourceConfig = new DatabaseConfig();
        sourceConfig.setUrl(props.getProperty("test.db.mysql.url"));
        sourceConfig.setUsername(props.getProperty("test.db.mysql.username"));
        sourceConfig.setPassword(props.getProperty("test.db.mysql.password"));
        sourceConfig.setDriverClassName(props.getProperty("test.db.mysql.driver"));

        targetConfig = new DatabaseConfig();
        targetConfig.setUrl(props.getProperty("test.db.mysql.url"));
        targetConfig.setUsername(props.getProperty("test.db.mysql.username"));
        targetConfig.setPassword(props.getProperty("test.db.mysql.password"));
        targetConfig.setDriverClassName(props.getProperty("test.db.mysql.driver"));
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
        cleanupResidualTestMappings();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (mappingId != null) {
                mappingService.stop(mappingId);
            }
        } catch (Exception e) {
            logger.warn("停止测试 mapping 失败（可能已停止）: {}", e.getMessage());
        }
        cleanupResidualTestMappings();
    }

    @Override
    protected Class<?> getTestClass() {
        return MixedKeyMysqlUpsertIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "MySQL 混合主键源连接器";
    }

    @Override
    protected String getTargetConnectorName() {
        return "MySQL 混合主键目标连接器";
    }

    @Override
    protected String getMappingName() {
        return "MySQL 混合主键 UPSERT 测试";
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
        return "MySQL";
    }

    @Override
    protected String getIncrementStrategy() {
        return "Log";
    }

    @Override
    protected String getDatabaseType(boolean isSource) {
        return "mysql";
    }

    @Test
    public void testUpsertMixedKey() throws Exception {
        logger.info("测试混合主键 UPSERT - 验证 WHERE 条件完整性...");

        // 创建连接器和映射（包含完整的 site_id 配置）
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);
        mappingId = createMapping();

        // 启动同步监听
        mappingService.start(mappingId);
        Thread.sleep(2000);

        // 插入 Alice (id=1)
        insertRow("mixed_t1", 1, "Alice", 25, sourceConfig);
        Thread.sleep(2000);
        assertEquals("目标表应有 1 行", 1L, countRows("mixed_t2", targetConfig));

        // 插入 Bob (id=2)
        insertRow("mixed_t1", 2, "Bob", 30, sourceConfig);
        Thread.sleep(2000);

        long targetCount = countRows("mixed_t2", targetConfig);
        assertEquals("目标表应有 2 行", 2L, targetCount);

        // 验证初始数据
        Map<String, Object> alice = queryRow("mixed_t2", "id=1", targetConfig);
        assertNotNull("Alice 应存在", alice);
        assertEquals("Alice Age 应为 25", 25, alice.get("age"));

        Map<String, Object> bob = queryRow("mixed_t2", "id=2", targetConfig);
        assertNotNull("Bob 应存在", bob);
        assertEquals("Bob Age 应为 30", 30, bob.get("age"));

        // 更新 Alice 的 Age 为 26
        updateRow("mixed_t1", 1, "Alice", 26, sourceConfig);
        Thread.sleep(3000);

        // 验证：只有 Alice 被更新，Bob 不变（核心验证点）
        targetCount = countRows("mixed_t2", targetConfig);
        assertEquals("目标表仍应有 2 行", 2L, targetCount);

        alice = queryRow("mixed_t2", "id=1", targetConfig);
        assertNotNull("Alice 应存在", alice);
        assertEquals("Alice Age 应为 26", 26, alice.get("age"));

        bob = queryRow("mixed_t2", "id=2", targetConfig);
        assertNotNull("Bob 应存在", bob);
        assertEquals("Bob Age 应保持 30", 30, bob.get("age"));

        logger.info("✅ 测试通过：只有 Alice 被更新，Bob 不受影响！");
    }

    @Override
    protected String createMapping() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("name", getMappingName());
        params.put("sourceConnectorId", sourceConnectorId);
        params.put("targetConnectorId", targetConnectorId);
        params.put("model", "increment");
        params.put("incrementStrategy", getIncrementStrategy());
        params.put("enableDDL", "true");
        params.put("enableInsert", "true");
        params.put("enableUpdate", "true");
        params.put("enableDelete", "true");
        mappingId = mappingService.add(params);

        Map<String, String> tableGroupParams = new HashMap<>();
        tableGroupParams.put("mappingId", mappingId);
        tableGroupParams.put("sourceTable", getSourceTableName());
        tableGroupParams.put("targetTable", getTargetTableName());
        tableGroupService.add(tableGroupParams);

        List<TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        if (!tableGroups.isEmpty()) {
            TableGroup tableGroup = tableGroups.get(0);

            Field siteIdField = tableGroup.getTargetTable().findColumnByName("site_id");
            java.util.List<FieldMapping> fieldMappings = new java.util.ArrayList<>(tableGroup.getFieldMapping());
            fieldMappings.add(new FieldMapping((Field) null, siteIdField));
            tableGroup.setFieldMapping(fieldMappings);

            Convert siteIdConvert = new Convert();
            siteIdConvert.setName("site_id");
            siteIdConvert.setConvertCode("FIXED");
            siteIdConvert.setArgs("5");
            siteIdConvert.setRoot(true);
            tableGroup.setConvert(java.util.Collections.singletonList(siteIdConvert));

            tableGroup.isInit = false;
            profileComponent.editConfigModel(tableGroup);
        }
        return mappingId;
    }

    private void insertRow(String table, int id, String name, int age, DatabaseConfig cfg) throws Exception {
        new DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("INSERT INTO %s (id, name, age) VALUES (%d, '%s', %d)", table, id, name, age));
                    return null;
                });
    }

    private void updateRow(String table, int id, String name, int age, DatabaseConfig cfg) throws Exception {
        new DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("UPDATE %s SET age=%d WHERE id=%d", table, age, id));
                    return null;
                });
    }

    private long countRows(String table, DatabaseConfig cfg) throws Exception {
        return (Long) new DatabaseConnectorInstance(cfg)
                .execute(t -> t.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
    }

    private Map<String, Object> queryRow(String table, String where, DatabaseConfig cfg) throws Exception {
        return (Map<String, Object>) new DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    List<Map<String, Object>> r = t.queryForList("SELECT * FROM " + table + " WHERE " + where + " LIMIT 1");
                    return r.isEmpty() ? null : r.get(0);
                });
    }
}
