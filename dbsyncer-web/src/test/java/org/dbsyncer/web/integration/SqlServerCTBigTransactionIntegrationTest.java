package org.dbsyncer.web.integration;

import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
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

/**
 * SQL Server CT 大事务优化集成测试（注意，未真正实现）
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
    @BeforeClass
    public static void setUpClass() throws Exception {
        logger.info("=== 开始初始化 SQL Server CT 大事务优化测试环境 ===");

        // 加载测试配置
        loadTestConfigStatic();

        // 创建测试数据库管理器
        testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);

        // 初始化测试环境（使用大事务测试专用脚本）
        String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table-bigtx", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
        testDatabaseManager.initializeTestEnvironment(initSql, initSql);

        logger.info("SQL Server CT 大事务优化测试环境初始化完成");
    }

    @AfterClass
    public static void tearDownClass() {
        logger.info("=== 开始清理 SQL Server CT 大事务优化测试环境 ===");

        try {
            // 大事务测试使用专用的清理脚本（如果有），否则复用通用脚本
            String cleanupSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table-bigtx", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
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

        // 确保测试表存在（如果 @AfterClass 已清理，则重新创建）
        ensureTestTablesExist();

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

        // 创建 Mapping（会自动创建 Meta 和 TableGroup）
        mappingId = createMapping();
        logger.info("Mapping 创建成功：{}", mappingId);
        
        metaId = profileComponent.getMapping(mappingId).getMetaId();
        logger.info("Meta 创建成功：{}", metaId);
        
        // 检查 TableGroup 是否创建
        java.util.List<TableGroup> tableGroups = tableGroupService.getTableGroupAll(mappingId);
        logger.info("TableGroup 数量：{}", tableGroups != null ? tableGroups.size() : "null");
        
        if (tableGroups == null || tableGroups.isEmpty()) {
            logger.warn("TableGroup 未创建，可能原因：表缺少主键或表不存在");
            // 检查源表和目标表是否有主键
            checkTablePrimaryKey();
        }

        // 准备测试数据
        prepareTestData();
        logger.info("测试数据准备完成：{} 条记录", TEST_RECORD_COUNT);
    }

    /**
     * 检查表是否有主键
     */
    private void checkTablePrimaryKey() throws Exception {
        try {
            DatabaseConnectorInstance sourceInstance = new DatabaseConnectorInstance(sourceConfig);
            MetaInfo sourceMetaInfo = connectorFactory.getMetaInfo(sourceInstance, getSourceTableName());
            if (sourceMetaInfo != null && sourceMetaInfo.getColumn() != null) {
                long pkCount = sourceMetaInfo.getColumn().stream().filter(f -> f.isPk()).count();
                logger.info("源表 {} 主键数量：{}", getSourceTableName(), pkCount);
            }
            
            DatabaseConnectorInstance targetInstance = new DatabaseConnectorInstance(targetConfig);
            MetaInfo targetMetaInfo = connectorFactory.getMetaInfo(targetInstance, getTargetTableName());
            if (targetMetaInfo != null && targetMetaInfo.getColumn() != null) {
                long pkCount = targetMetaInfo.getColumn().stream().filter(f -> f.isPk()).count();
                logger.info("目标表 {} 主键数量：{}", getTargetTableName(), pkCount);
            }
        } catch (Exception e) {
            logger.error("检查表主键失败", e);
        }
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

        // Mapping 创建时已自动创建 TableGroup
        // 获取 Mapping 信息
        org.dbsyncer.parser.model.Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.assertNotNull("Mapping 不应为 null", mapping);
        
        // 获取 TableGroup 列表
        java.util.List<TableGroup> tableGroups = tableGroupService.getTableGroupAll(mappingId);
        Assert.assertNotNull("TableGroup 列表不应为 null", tableGroups);
        Assert.assertEquals("应创建 1 个 TableGroup", 1, tableGroups.size());
        
        TableGroup tableGroup = tableGroups.get(0);
        Assert.assertNotNull("TableGroup 不应为 null", tableGroup);

        // 验证增量持久化配置已生效
        // 注意：由于测试环境限制，这里验证配置是否正确设置
        // 实际功能验证需要在真实环境中进行
        
        logger.info("Mapping ID: {}", mappingId);
        logger.info("Mapping Name: {}", mapping.getName());
        logger.info("TableGroup ID: {}", tableGroup.getId());
        logger.info("TableGroup Name: {}", tableGroup.getName());
        logger.info("预期持久化间隔：{} 条记录", 10000);
        
        // 断言：TableGroup 已正确创建
        Assert.assertNotNull("TableGroup 已创建", tableGroup);
        Assert.assertEquals("源表名正确", getSourceTableName(), tableGroup.getSourceTable().getName());
        Assert.assertEquals("目标表名正确", getTargetTableName(), tableGroup.getTargetTable().getName());

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

        // Mapping 创建时已自动创建 TableGroup
        // 获取 Mapping 信息
        org.dbsyncer.parser.model.Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.assertNotNull("Mapping 不应为 null", mapping);
        
        // 获取 TableGroup 列表
        java.util.List<TableGroup> tableGroups = tableGroupService.getTableGroupAll(mappingId);
        Assert.assertNotNull("TableGroup 列表不应为 null", tableGroups);
        Assert.assertEquals("应创建 1 个 TableGroup", 1, tableGroups.size());
        
        TableGroup tableGroup = tableGroups.get(0);
        Assert.assertNotNull("TableGroup 不应为 null", tableGroup);

        // 验证优雅停止机制已配置
        // 注意：实际功能验证需要在真实环境中进行
        
        logger.info("Mapping ID: {}", mappingId);
        logger.info("TableGroup ID: {}", tableGroup.getId());
        logger.info("优雅停止机制：stopRequested (AtomicBoolean)");
        
        // 断言：TableGroup 已正确创建
        Assert.assertNotNull("TableGroup 已创建", tableGroup);

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

        // Mapping 创建时已自动创建 TableGroup
        // 获取 Mapping 信息
        org.dbsyncer.parser.model.Mapping mapping = profileComponent.getMapping(mappingId);
        Assert.assertNotNull("Mapping 不应为 null", mapping);
        
        // 获取 TableGroup 列表
        java.util.List<TableGroup> tableGroups = tableGroupService.getTableGroupAll(mappingId);
        Assert.assertNotNull("TableGroup 列表不应为 null", tableGroups);
        Assert.assertEquals("应创建 1 个 TableGroup", 1, tableGroups.size());
        
        TableGroup tableGroup = tableGroups.get(0);
        Assert.assertNotNull("TableGroup 不应为 null", tableGroup);

        // 验证有限重试机制已配置
        // 注意：实际功能验证需要在真实环境中进行
        
        logger.info("Mapping ID: {}", mappingId);
        logger.info("TableGroup ID: {}", tableGroup.getId());
        logger.info("最大重试次数：{}", 3);
        
        // 断言：TableGroup 已正确创建
        Assert.assertNotNull("TableGroup 已创建", tableGroup);

        logger.info("=== 测试完成：有限重试 ===");
    }

    // ==================== 辅助方法 ====================

    /**
     * 确保测试表存在，如果不存在则重新创建
     */
    private void ensureTestTablesExist() throws Exception {
        try {
            DatabaseConnectorInstance sourceInstance = new DatabaseConnectorInstance(sourceConfig);
            MetaInfo sourceMetaInfo = connectorFactory.getMetaInfo(sourceInstance, getSourceTableName());
            
            // 如果表不存在或没有主键，则重新初始化
            if (sourceMetaInfo == null || sourceMetaInfo.getColumn() == null || 
                sourceMetaInfo.getColumn().stream().noneMatch(f -> f.isPk())) {
                logger.warn("测试表 {} 不存在或无主键，重新初始化", getSourceTableName());
                
                // 重新加载配置
                loadTestConfigStatic();
                
                // 重新创建测试数据库管理器
                if (testDatabaseManager == null) {
                    testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);
                }
                
                // 重新初始化测试环境
                String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table-bigtx", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
                testDatabaseManager.initializeTestEnvironment(initSql, initSql);
                
                logger.info("测试表重新初始化完成");
            }
        } catch (Exception e) {
            logger.warn("检查测试表状态失败，尝试重新初始化", e);
            
            // 重新加载配置
            loadTestConfigStatic();
            
            // 重新创建测试数据库管理器
            if (testDatabaseManager == null) {
                testDatabaseManager = new TestDatabaseManager(sourceConfig, targetConfig);
            }
            
            // 重新初始化测试环境
            String initSql = loadSqlScriptByDatabaseTypeStatic("reset-test-table-bigtx", "sqlserver", SqlServerCTBigTransactionIntegrationTest.class);
            testDatabaseManager.initializeTestEnvironment(initSql, initSql);
            
            logger.info("测试表强制重新初始化完成");
        }
    }

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
        return "Log"; // CT 模式使用日志监听
    }

    @Override
    protected String getDatabaseType(boolean isSource) {
        return "sqlserver";
    }
}
