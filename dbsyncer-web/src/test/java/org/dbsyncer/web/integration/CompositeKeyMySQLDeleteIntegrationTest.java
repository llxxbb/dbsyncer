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
 * MySQL 复合主键 DELETE 集成测试 - 增量模式
 * 测试联合主键 (Uid, Courseid) 场景下 DELETE 的正确性
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class CompositeKeyMySQLDeleteIntegrationTest extends BaseDDLIntegrationTest {

    private static String sourceTableName;
    private static String targetTableName;
    private static DatabaseConfig sourceConfig;
    private static DatabaseConfig targetConfig;
    private static TestDatabaseManager testDatabaseManager;

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("开始初始化 MySQL 复合主键 DELETE 集成测试环境 (增量模式)");
        loadTestConfigStatic();

        sourceTableName = "test_delete_from";
        targetTableName = "test_delete_to";

        String initSql = "DROP TABLE IF EXISTS " + sourceTableName + "," + targetTableName + "; CREATE TABLE " + sourceTableName + " (DetailID int(11) DEFAULT NULL, HandEnd tinyint(1) DEFAULT NULL, Operator varchar(30) DEFAULT NULL, Courseid varchar(10) NOT NULL, PayMoney decimal(19,4) DEFAULT NULL, payCourseID bigint(20) NOT NULL, Payed varchar(1) NOT NULL, favourablemoney decimal(19,4) DEFAULT NULL, Uid int(11) NOT NULL, DownLoad varchar(1) DEFAULT NULL, DatePay datetime DEFAULT NULL, DateEnd datetime DEFAULT NULL, DateSel datetime DEFAULT NULL, AreaID int(11) DEFAULT NULL, GetMoney decimal(19,4) DEFAULT NULL, updatetime datetime(3) DEFAULT NULL, PRIMARY KEY (Uid,Courseid)); CREATE TABLE " + targetTableName + " (DetailID int(11) DEFAULT NULL, HandEnd tinyint(1) DEFAULT NULL, Operator varchar(30) DEFAULT NULL, Courseid varchar(10) NOT NULL, PayMoney decimal(19,4) DEFAULT NULL, payCourseID bigint(20) NOT NULL, Payed varchar(1) NOT NULL, favourablemoney decimal(19,4) DEFAULT NULL, Uid int(11) NOT NULL, DownLoad varchar(1) DEFAULT NULL, DatePay datetime DEFAULT NULL, DateEnd datetime DEFAULT NULL, DateSel datetime DEFAULT NULL, AreaID int(11) DEFAULT NULL, GetMoney decimal(19,4) DEFAULT NULL, updatetime datetime(3) DEFAULT NULL, PRIMARY KEY (Uid,Courseid))";
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("MySQL 复合主键 DELETE 集成测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("开始清理测试环境");
        try {
            testDatabaseManager.cleanupTestEnvironment("DROP TABLE IF EXISTS " + sourceTableName + "," + targetTableName, "DROP TABLE IF EXISTS " + sourceTableName + "," + targetTableName);
        } catch (Exception e) {
            logger.error("清理失败", e);
        }
    }

    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = CompositeKeyMySQLDeleteIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
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
        logger.info("开始测试：复合主键 DELETE");
        // 清理所有残留的 mapping，确保测试环境干净
        cleanupResidualTestMappings();
    }

    @Override
    protected Class<?> getTestClass() {
        return CompositeKeyMySQLDeleteIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "MySQL 复合主键源连接器";
    }

    @Override
    protected String getTargetConnectorName() {
        return "MySQL 复合主键目标连接器";
    }

    @Override
    protected String getMappingName() {
        return "MySQL 复合主键 DELETE 测试";
    }

    @Override
    protected String getSourceTableName() {
        return null;
    }

    @Override
    protected String getTargetTableName() {
        return null;
    }

    @Override
    protected List<String> getInitialFieldMappings() {
        return Arrays.asList(
            "DetailID,DetailID",
            "HandEnd,HandEnd",
            "Operator,Operator",
            "Courseid,Courseid",
            "PayMoney,PayMoney",
            "payCourseID,payCourseID",
            "Payed,Payed",
            "favourablemoney,favourablemoney",
            "Uid,Uid",
            "DownLoad,DownLoad",
            "DatePay,DatePay",
            "DateEnd,DateEnd",
            "DateSel,DateSel",
            "AreaID,AreaID",
            "GetMoney,GetMoney",
            "updatetime,updatetime"
        );
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
    public void testDeleteCompositeKey() throws Exception {
        logger.info("测试复合主键 DELETE (增量模式)...");

        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);
        mappingId = createMapping();

        List<org.dbsyncer.sdk.model.TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
        org.dbsyncer.sdk.model.TableGroup tableGroup = tableGroups.get(0);
        tableGroup.setFullCompleted(false);
        tableGroup.setCursors(null);
        profileComponent.editConfigModel(tableGroup);

        mappingService.start(mappingId);
        Thread.sleep(2000);

        insertRowDirect(sourceTableName, 1, "C001", 1.0, 100.00, 1001L, "Y", "admin1", sourceConfig);
        insertRowDirect(sourceTableName, 2, "C002", 2.0, 200.00, 1002L, "Y", "admin2", sourceConfig);
        insertRowDirect(sourceTableName, 3, "C003", 3.0, 300.00, 1003L, "N", "admin3", sourceConfig);

        String deleteSql = String.format("DELETE FROM %s WHERE Uid = 1 AND Courseid = 'C001'", sourceTableName);
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(sourceConfig)
                .execute(t -> {
                    t.execute(deleteSql);
                    return null;
                });
        Thread.sleep(2000);

        long targetCount = countRows(targetTableName, targetConfig);
        assertEquals("目标表应有 2 行", 2L, targetCount);
    }

    private void insertRowDirect(String table, int uid, String courseid, double detailId, Double payMoney, Long payCourseId, String payed, String operator, DatabaseConfig cfg) throws Exception {
        new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    t.execute(String.format("INSERT INTO %s (Uid, Courseid, DetailID, PayMoney, payCourseID, Payed, Operator) VALUES (%d, '%s', %s, %s, %s, '%s', '%s')",
                            table, uid, courseid, detailId, payMoney, payCourseId, payed, operator));
                    return null;
                });
    }

    private long countRows(String table, DatabaseConfig cfg) throws Exception {
        return (Long) new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> t.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
    }

    private Map<String, Object> queryRowByCompositeKey(String table, int uid, String courseid, DatabaseConfig cfg) throws Exception {
        return (Map<String, Object>) new org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance(cfg)
                .execute(t -> {
                    List<Map<String, Object>> r = t.queryForList(
                        "SELECT * FROM " + table + " WHERE Uid = ? AND Courseid = ? LIMIT 1",
                        uid, courseid);
                    return r.isEmpty() ? null : r.get(0);
                });
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
        tableGroupParams.put("sourceTable", sourceTableName);
        tableGroupParams.put("targetTable", targetTableName);
        tableGroupParams.put("fieldMappings", String.join(",", getInitialFieldMappings()));
        tableGroupService.add(tableGroupParams);
        return mappingId;
    }
}
