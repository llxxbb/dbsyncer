package org.dbsyncer.web.integration;

import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.web.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * SQL Server CT 大事务优化集成测试
 * 
 * 测试场景：
 * 1. 增量持久化：每 10000 条记录持久化一次，中断后重复处理不超过 1 万条
 * 2. 优雅停止：前端停止指令后，已处理数据进度已持久化
 * 3. 有限重试：同一版本连续失败 3 次后停止同步
 * 
 * @see <a href="file:../../doc/adr/0004-sqlserver-ct-bigtx-optimization.md">ADR 0004</a>
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
public class SqlServerCTBigTransactionIntegrationTest extends BaseDDLIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(SqlServerCTBigTransactionIntegrationTest.class);

    // 测试配置
    private static final int TEST_RECORD_COUNT = 100000;  // 10 万条测试数据
    private static final int SNAPSHOT_INTERVAL = 10000;   // 每 10000 条持久化
    private static final int MAX_RETRY = 3;               // 最大重试次数

    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("=== 开始初始化 SQL Server CT 大事务优化测试环境 ===");

        // 加载测试配置
        loadTestConfigStatic();

        // 创建测试数据库管理器
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        // 初始化测试环境
        String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("SQL Server CT 大事务优化测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("=== 开始清理 SQL Server CT 大事务优化测试环境 ===");

        try {
            String cleanupSql = loadSqlScriptByDatabaseTypeStatic("cleanup-test-data", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
            testDatabaseManager.cleanupTestEnvironment(cleanupSql, cleanupSql);
            logger.info("测试环境清理完成");
        } catch (Exception e) {
            logger.error("清理测试环境失败", e);
        }

        testDatabaseManager = null;
    }

    @Before
    public void setUp() throws Exception {
        logger.info("=== 测试前准备 ===");

        // 加载测试配置
        loadTestConfig();

        // 设置连接器类型
        if (sourceConfig.getConnectorType() == null) {
            sourceConfig.setConnectorType(getConnectorType(sourceConfig, true));
        }
        if (targetConfig.getConnectorType() == null) {
            targetConfig.setConnectorType(getConnectorType(targetConfig, false));
        }

        // 创建连接器
        sourceConnectorId = createConnector(getSourceConnectorName(), sourceConfig, true);
        targetConnectorId = createConnector(getTargetConnectorName(), targetConfig, false);

        // 创建 Mapping（会自动创建 Meta）
        mappingId = createMapping();
        metaId = profileComponent.getMapping(mappingId).getMetaId();

        // 准备测试数据
        prepareTestData();
        logger.info("测试数据准备完成：{} 条记录", TEST_RECORD_COUNT);
    }

    @After
    public void tearDown() throws Exception {
        logger.info("=== 测试后清理 ===");

        if (mappingId != null) {
            mappingService.remove(mappingId);
            logger.info("已删除 Mapping: {}", mappingId);
        }

        if (sourceConnectorId != null) {
            connectorService.remove(sourceConnectorId);
            logger.info("已删除源连接器：{}", sourceConnectorId);
        }

        if (targetConnectorId != null) {
            connectorService.remove(targetConnectorId);
            logger.info("已删除目标连接器：{}", targetConnectorId);
        }

        // Meta 会随 Mapping 一起删除，不需要单独删除
        logger.info("已清理测试环境");
    }

    /**
     * 场景 1：增量持久化测试
     * 
     * 验证：处理 10 万条数据时，中断后重试重复处理不超过 1 万条
     */
    @Test
    public void testIncrementalSnapshot() throws Exception {
        logger.info("=== 开始测试：增量持久化 ===");

        // 创建 TableGroup
        String tableGroupId = createTableGroup();

        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger snapshotCount = new AtomicInteger(0);

        // 模拟中断：处理到 50% 时停止
        int stopAt = TEST_RECORD_COUNT / 2;

        // TODO: 实现测试逻辑
        // 1. 启动 SqlServerCTListener
        // 2. 监听 processedCount，达到 stopAt 时调用 stopGracefully()
        // 3. 记录 snapshotCount
        // 4. 重启后验证重复处理不超过 SNAPSHOT_INTERVAL

        logger.info("预期停止位置：{}", stopAt);
        logger.info("预期持久化次数：{}", TEST_RECORD_COUNT / SNAPSHOT_INTERVAL);

        // 断言：重复处理不超过 SNAPSHOT_INTERVAL
        // Assert.assertTrue("重复处理超过 1 万条", duplicateCount <= SNAPSHOT_INTERVAL);

        logger.info("=== 测试完成：增量持久化 ===");
    }

    /**
     * 场景 2：优雅停止测试
     * 
     * 验证：前端发送停止指令后，已处理数据进度已持久化
     */
    @Test
    public void testGracefulStop() throws Exception {
        logger.info("=== 开始测试：优雅停止 ===");

        // 创建 TableGroup
        String tableGroupId = createTableGroup();

        CountDownLatch stopLatch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);

        // TODO: 实现测试逻辑
        // 1. 启动 SqlServerCTListener
        // 2. 处理到 50% 时调用 stopGracefully()
        // 3. 验证 lastSuccessfulVersion 已持久化
        // 4. 重启后从 lastSuccessfulVersion 恢复

        logger.info("处理记录数：{}", processedCount.get());

        // 断言：进度已持久化
        // Assert.assertTrue("进度未持久化", lastSuccessfulVersion > 0);

        logger.info("=== 测试完成：优雅停止 ===");
    }

    /**
     * 场景 3：有限重试测试
     * 
     * 验证：同一版本连续失败 3 次后停止同步
     */
    @Test
    public void testLimitedRetry() throws Exception {
        logger.info("=== 开始测试：有限重试 ===");

        // 创建 TableGroup
        String tableGroupId = createTableGroup();

        AtomicInteger retryCount = new AtomicInteger(0);

        // TODO: 实现测试逻辑
        // 1. 模拟 SQL Server 超时 kill 连接
        // 2. 验证 currentVersionRetryCount 递增
        // 3. 达到 MAX_RETRY 后停止同步

        logger.info("最大重试次数：{}", MAX_RETRY);
        logger.info("实际重试次数：{}", retryCount.get());

        // 断言：重试次数不超过 MAX_RETRY
        // Assert.assertTrue("重试次数超过上限", retryCount.get() <= MAX_RETRY);

        logger.info("=== 测试完成：有限重试 ===");
    }

    // ==================== 辅助方法 ====================

    /**
     * 静态方法版本的 loadTestConfig，用于@BeforeClass/@AfterClass
     */
    private static void loadTestConfigStatic() throws IOException {
        Properties props = new Properties();
        try (InputStream input = SqlServerCTBigTransactionIntegrationTest.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                logger.warn("未找到 test.properties 配置文件，使用默认配置");
                sourceConfig = createDefaultSQLServerConfig();
                targetConfig = createDefaultSQLServerConfig();
                return;
            }
            props.load(input);
        }

        // 创建源数据库配置 (SQL Server CT)
        sourceConfig = new DatabaseConfig();
        sourceConfig.setUrl(props.getProperty("test.db.sqlserver.url", "jdbc:sqlserver://192.168.192.240:1433;DatabaseName=source_db;encrypt=false;trustServerCertificate=true"));
        sourceConfig.setUsername(props.getProperty("test.db.sqlserver.username", "test_user"));
        sourceConfig.setPassword(props.getProperty("test.db.sqlserver.password", "test_password"));
        sourceConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));

        // 创建目标数据库配置 (SQL Server CT)
        targetConfig = new DatabaseConfig();
        targetConfig.setUrl(props.getProperty("test.db.sqlserver.url", "jdbc:sqlserver://192.168.192.240:1433;DatabaseName=target_db;encrypt=false;trustServerCertificate=true"));
        targetConfig.setUsername(props.getProperty("test.db.sqlserver.username", "test_user"));
        targetConfig.setPassword(props.getProperty("test.db.sqlserver.password", "test_password"));
        targetConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
    }

    /**
     * 实例方法版本的 loadTestConfig，用于@Before
     */
    private void loadTestConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("test.properties")) {
            if (input == null) {
                logger.warn("未找到 test.properties 配置文件，使用默认配置");
                sourceConfig = createDefaultSQLServerConfig();
                targetConfig = createDefaultSQLServerConfig();
                return;
            }
            props.load(input);
        }

        // 创建源数据库配置 (SQL Server CT)
        sourceConfig = new DatabaseConfig();
        sourceConfig.setUrl(props.getProperty("test.db.sqlserver.url", "jdbc:sqlserver://192.168.192.240:1433;DatabaseName=source_db;encrypt=false;trustServerCertificate=true"));
        sourceConfig.setUsername(props.getProperty("test.db.sqlserver.username", "test_user"));
        sourceConfig.setPassword(props.getProperty("test.db.sqlserver.password", "test_password"));
        sourceConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));

        // 创建目标数据库配置 (SQL Server CT)
        targetConfig = new DatabaseConfig();
        targetConfig.setUrl(props.getProperty("test.db.sqlserver.url", "jdbc:sqlserver://192.168.192.240:1433;DatabaseName=target_db;encrypt=false;trustServerCertificate=true"));
        targetConfig.setUsername(props.getProperty("test.db.sqlserver.username", "test_user"));
        targetConfig.setPassword(props.getProperty("test.db.sqlserver.password", "test_password"));
        targetConfig.setDriverClassName(props.getProperty("test.db.sqlserver.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
    }

    /**
     * 创建默认 SQL Server 配置
     */
    protected static DatabaseConfig createDefaultSQLServerConfig() {
        DatabaseConfig config = new DatabaseConfig();
        config.setUrl("jdbc:sqlserver://192.168.192.240:1433;DatabaseName=test;encrypt=false;trustServerCertificate=true");
        config.setUsername("test_user");
        config.setPassword("test_password");
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return config;
    }

    /**
     * 准备测试数据
     */
    private void prepareTestData() throws Exception {
        // 使用 TestDatabaseManager 准备测试数据
        // 数据会在 @BeforeClass 中通过 reset-test-table-sqlserver.sql 脚本创建
        logger.info("测试数据已通过 TestDatabaseManager 准备");
    }

    /**
     * 创建 TableGroup
     */
    private String createTableGroup() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("mappingId", mappingId);
        params.put("sourceTable", getSourceTableName());
        params.put("targetTable", getTargetTableName());
        params.put("fieldMappings", String.join(",", getInitialFieldMappings()));
        
        return tableGroupService.add(params);
    }

    // ==================== 实现 BaseDDLIntegrationTest 的抽象方法 ====================

    @Override
    protected Class<?> getTestClass() {
        return SqlServerCTBigTransactionIntegrationTest.class;
    }

    @Override
    protected String getSourceConnectorName() {
        return "SQLServerCT-Source-BigTx";
    }

    @Override
    protected String getTargetConnectorName() {
        return "SQLServerCT-Target-BigTx";
    }

    @Override
    protected String getMappingName() {
        return "SQLServerCT-BigTx-Test";
    }

    @Override
    protected String getSourceTableName() {
        return "test_bigtx_source";
    }

    @Override
    protected String getTargetTableName() {
        return "test_bigtx_target";
    }

    @Override
    protected List<String> getInitialFieldMappings() {
        return Arrays.asList(
            "id|id",
            "name|name",
            "age|age",
            "gender|gender",
            "create_time|create_time"
        );
    }

    @Override
    protected String getConnectorType(DatabaseConfig config, boolean isSource) {
        return "SqlServerCT";
    }

    @Override
    protected String getIncrementStrategy() {
        return "CT";
    }

    @Override
    protected String getDatabaseType(boolean isSource) {
        return "sqlserver";
    }
}
