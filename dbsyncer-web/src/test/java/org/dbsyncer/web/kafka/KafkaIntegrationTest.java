package org.dbsyncer.web.kafka;

import org.dbsyncer.biz.ConnectorService;
import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.web.Application;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 集成测试 - 独立消费者 + 独立 TableGroup 模式
 * 
 * 核心优势:
 * 1. 每个测试有独立的 TableGroup 配置 (天然隔离)
 * 2. 每个测试有独立的消费者 (天然隔离)
 * 3. 无需清理共享状态
 * 
 * 配置来源：test.properties
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles("test")
public class KafkaIntegrationTest {

    @Resource
    protected ConnectorService connectorService;

    @Resource
    protected MappingService mappingService;

    @Resource
    protected TableGroupService tableGroupService;

    @Value("${test.db.mysql.url}")
    protected String mysqlUrl;

    @Value("${test.db.mysql.username}")
    protected String mysqlUsername;

    @Value("${test.db.mysql.password}")
    protected String mysqlPassword;

    @Value("${test.db.mysql.driver}")
    protected String mysqlDriver;

    @Value("${test.kafka.bootstrap.servers:localhost:9092}")
    protected String kafkaBootstrapServers;

    @Value("${test.kafka.group.id:syncer-test}")
    protected String kafkaGroupId;

    protected static DatabaseConfig mysqlConfig;
    
    protected String connectorId;
    protected String kafkaConnectorId;
    protected Map<String, String> kafkaConnectorParams;
    protected String mappingId;
    protected String createTableGroupId;
    protected String testTable = "test_table_kafka";
    protected static KafkaTestDatabaseManager testKafkaManager;

    @PostConstruct
    public void init() {
        try {
            mysqlConfig = new DatabaseConfig();
            mysqlConfig.setUrl(mysqlUrl);
            mysqlConfig.setUsername(mysqlUsername);
            mysqlConfig.setPassword(mysqlPassword);
            mysqlConfig.setDriverClassName(mysqlDriver);
            mysqlConfig.setSchema("dp_test");
            mysqlConfig.setConnectorType("MySQL");
        } catch (Exception e) {
            System.err.println("数据库配置初始化失败：" + e.getMessage());
        }
    }

    @Before
    public void setUp() throws Exception {
        System.out.println("=== 创建共享连接器 ===");
        
        // 创建 MySQL 源连接器 (共享)
        Map<String, String> sourceParams = new HashMap<>();
        sourceParams.put("name", "kafka-test-mysql-source-shared");
        sourceParams.put("connectorType", "MySQL");
        sourceParams.put("url", mysqlConfig.getUrl());
        sourceParams.put("username", mysqlConfig.getUsername());
        sourceParams.put("password", mysqlConfig.getPassword());
        sourceParams.put("driverClassName", mysqlConfig.getDriverClassName());
        connectorId = connectorService.add(sourceParams);

        // 初始化 Manager (首次)
        if (testKafkaManager == null && mysqlConfig != null) {
            testKafkaManager = new KafkaTestDatabaseManager(
                mysqlConfig.getUrl(), mysqlConfig.getUsername(), mysqlConfig.getPassword(), 
                mysqlConfig.getDriverClassName(), kafkaBootstrapServers, kafkaGroupId
            );
            
            System.out.println("DEBUG: 测试数据库初始化");
            
            // 创建 database
            testKafkaManager.createDatabaseIfNotExists("dp_test");
            
            // 创建 table
            System.out.println("DEBUG: 创建表 " + testTable);
            testKafkaManager.executeUpdate(
                "DROP TABLE IF EXISTS " + testTable + " CASCADE"
            );
            testKafkaManager.executeUpdate(
                "CREATE TABLE " + testTable + 
                " (id VARCHAR(64) PRIMARY KEY, name VARCHAR(100), value INT, enabled BOOLEAN)"
            );
            System.out.println("DEBUG: 测试数据库初始化完成");
        }

        // 创建 Kafka 目标连接器 (共享，不提供 topic)
        kafkaConnectorParams = createKafkaConnectorParams();
        kafkaConnectorId = connectorService.add(kafkaConnectorParams);

        System.out.println("=== 共享资源创建完成 ===");
        System.out.println("Connector: " + connectorId);
        System.out.println("Mapping: " + mappingId);
        System.out.println("Table: " + testTable);
    }

    @After
    public void tearDown() throws Exception {
        // 停止 mapping
        if (mappingId != null) {
            try { mappingService.remove(mappingId); } catch (Exception e) {}
        }
        
        // 清理连接器
        if (connectorId != null) {
            try { connectorService.remove(connectorId); } catch (Exception e) {}
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (testKafkaManager != null) {
            testKafkaManager.cleanup();
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, String> createKafkaConnectorParams() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "kafka-test-sink-shared");
        params.put("connectorType", "Kafka");
        params.put("bootstrapServers", kafkaBootstrapServers);
        params.put("groupId", kafkaGroupId);
        params.put("sessionTimeoutMs", "30000");
        params.put("heartbeatIntervalMs", "10000");
        params.put("requestTimeoutMs", "45000");
        params.put("fetchMaxWaitMs", "500");
        params.put("serializer", "org.dbsyncer.connector.kafka.serialization.MapToJsonSerializer");
        params.put("deserializer", "org.dbsyncer.connector.kafka.serialization.JsonToMapDeserializer");
        params.put("acks", "all");
        params.put("maxPartitionFetchBytes", "1048576");
        params.put("bufferMemory", "33554432");
        params.put("batchSize", "16384");
        params.put("maxRequestSize", "1048576");
        return params;
    }

    /**
     * 创建测试特定的 TableGroup (每个测试调用)
     */
    private String createTableGroupParams() {
        String fieldMappings = "id:id,name:name,value:value,enabled:enabled";
        Map<String, String> tableGroupParams = new HashMap<>();
        tableGroupParams.put("mappingId", mappingId);
        tableGroupParams.put("sourceTable", testTable);
        tableGroupParams.put("targetTable", testTable);
        tableGroupParams.put("fieldMappings", fieldMappings);
        try {
            return tableGroupService.add(tableGroupParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建 mapping
     */
    private String createMapping(String topic) {
        Map<String, String> mappingParams = new HashMap<>();
        mappingParams.put("name", "kafka-test-mapping-shared");
        mappingParams.put("sourceConnectorId", connectorId);
        mappingParams.put("targetConnectorId", kafkaConnectorId);
        mappingParams.put("model", "increment");
        mappingParams.put("enableDDL", "true");
        mappingParams.put("enableInsert", "true");
        mappingParams.put("enableUpdate", "true");
        mappingParams.put("incrementStrategy", "Log");
        if (topic != null && !topic.isEmpty()) {
            Map<String, String> params = new HashMap<>();
            params.put("topic", topic);
            mappingParams.put("commonParams", JsonUtil.objToJson(params));
        }
        try {
            String mappingId = mappingService.add(mappingParams);
            mappingParams.put("id", mappingId); 
            mappingService.edit(mappingParams);
            return mappingId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行 Kafka 测试
     */
    protected Map<String, Object> executeKafkaTest(String testTopic, String sql) throws Exception {
        createTableGroupParams();
        mappingService.start(mappingId);
        
        KafkaTestDatabaseManager testConsumer = new KafkaTestDatabaseManager(
            mysqlConfig.getUrl(), mysqlConfig.getUsername(), mysqlConfig.getPassword(), 
            mysqlConfig.getDriverClassName(), kafkaBootstrapServers, kafkaGroupId + "-test-" + testTopic.replaceAll("[-.:]", "-")
        );
        
        testConsumer.startConsumerAsync(testTopic);
        
        Thread.sleep(2000);
        
        testConsumer.executeUpdate(sql);
        
        Thread.sleep(3000);
        
        KafkaTestDatabaseManager.CacheMessage msg = testConsumer.getMessage(testTopic, 5000);
        Assert.assertNotNull("应收到至少 1 条 Kafka 消息", msg);

        System.out.println("=== " + testTopic + " 通过：捕获到 1 条消息 ===");
        
        validateMessage(testTopic, msg);
        return (Map<String, Object>)msg.value;
    }
    
    /**
     * 验证 Kafka 消息
     */
    private void validateMessage(String testTopic, KafkaTestDatabaseManager.CacheMessage msg) {

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) msg.value;

        String subType = (String) message.get("subType");
        Assert.assertFalse("消息 subType 字段不能为空字符串", subType != null && subType.isEmpty());

        String dataSourceType = (String) message.get("dataSourceType");
        Assert.assertEquals("消息 dataSourceType 字段不能为空字符串", "MySQL", dataSourceType);

        String sourceAddress = (String) message.get("sourceAddress");
        Assert.assertFalse("消息 sourceAddress 字段不能为空字符串", sourceAddress != null && sourceAddress.isEmpty());

        String sourceDBName = (String) message.get("sourceDBName");
        Assert.assertFalse("消息 sourceDBName 字段不能为空字符串", sourceDBName != null && sourceDBName.isEmpty());

        String tableName = (String) message.get("tableName");
        Assert.assertEquals("消息 tableName 字段不能为空字符串", testTable, tableName );

        long eventTimestamp = (Long) message.get("eventTimestamp");
        Assert.assertTrue("消息 eventTimestamp 字段大于0", eventTimestamp > 0);

        long collectTimestamp = (Long) message.get("collectTimestamp");
        Assert.assertTrue("消息 collectTimestamp 字段大于0", collectTimestamp > 0);
    }

    private void verifyDML(Map<String, Object> message) {

        String type = (String) message.get("type");
        Assert.assertEquals("消息 type 字段不能为空字符串", "DML", type);


        Map<String, Object> dbValue = (Map<String, Object>)message.get("value");


        String id = (String) dbValue.get("id");
        Assert.assertFalse("消息 id 字段不能为空字符串", id != null && id.isEmpty());
        
        String name = (String) dbValue.get("name");
        Assert.assertFalse("消息 name 字段不能为空字符串", name != null && name.isEmpty());
        
        Object value = dbValue.get("value");
        Assert.assertNotNull("消息 value 字段不能为 null", value);
        
        Object enabled = dbValue.get("enabled");
        Assert.assertNotNull("消息 enabled 字段不能为 null", enabled);
    }

    // ==================== 测试方法 ====================
    @Test
    public void testT1_DmlWithMappingTopic() throws Exception {
        System.out.println("\n=== T1: DML + mapping topic（topic 优先级）===");
        
        String testTopic = "kafka-test-topic-t1";
        String sql = "INSERT INTO " + testTable + " (id, name, value, enabled) VALUES ('test-pk-t1', 't1_test', 100, true) ON DUPLICATE KEY UPDATE name='t1_test', value=100, enabled=true";
        
        mappingId = createMapping(testTopic);
        Map<String, Object> message = executeKafkaTest(testTopic, sql);
        verifyDML(message);
    }

    @Test
    public void testT2_DmlWithConnectorTopic() throws Exception {
        System.out.println("\n=== T2: DML + connector topic（回退）===");

        String testTopic = "kafka-test-connector-topic";
        kafkaConnectorParams.put("id", kafkaConnectorId);
        kafkaConnectorParams.put("topic", testTopic);
        connectorService.edit(kafkaConnectorParams);
        String sql = "INSERT INTO " + testTable + " (id, name, value, enabled) VALUES ('test-pk-t2', 't2_test', 200, false) ON DUPLICATE KEY UPDATE name='t2_test', value=200, enabled=false";

        mappingId = createMapping(null);
        Map<String, Object> message = executeKafkaTest(testTopic, sql);
        verifyDML(message);
    }

    @Test
    public void testT3_DdlWithMappingTopic() throws Exception {
        System.out.println("\n=== T3: DDL + mapping topic===");

        String testTopic = "kafka-test-topic-t3";
        String sql = "ALTER TABLE " + testTable + " ADD COLUMN des1 VARCHAR(255) DEFAULT NULL COMMENT '描述' AFTER name";
        mappingId = createMapping(testTopic);
        Map<String, Object> message = executeKafkaTest(testTopic, sql);
        verifyDDL(message);
    }

        @Test
    public void testT4_DdlWithConnectorTopic() throws Exception {
        System.out.println("\n=== T4: DDL + connector topic===");

        String testTopic = "kafka-test-connector-topic";
        String sql = "ALTER TABLE " + testTable + " ADD COLUMN des2 VARCHAR(255) DEFAULT NULL COMMENT '描述' AFTER name";
        kafkaConnectorParams.put("id", kafkaConnectorId);
        kafkaConnectorParams.put("topic", testTopic);
        connectorService.edit(kafkaConnectorParams);
        mappingId = createMapping(null);
        Map<String, Object> message = executeKafkaTest(testTopic, sql);
        verifyDDL(message);
    }

    private void verifyDDL(Map<String, Object> message) {
        String msgSql = (String)message.get("ddlSql");
        Assert.assertTrue("消息 ddlSql 字段不为空", msgSql != null && StringUtil.isNotBlank(msgSql));
        
        String type = (String) message.get("type");
        Assert.assertTrue("消息 type 应为 DDL", type != null && type.equals("DDL"));
        
        String subType = (String) message.get("subType");
        Assert.assertTrue("消息 subType 应以 ALTER开头", subType.startsWith("ALTER"));
    }
}
