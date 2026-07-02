package org.dbsyncer.web.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.*;

/**
 * Kafka 测试数据库管理器 - 持久消费者模式
 * 
 * 核心特性:
 * 1. @Test 启动消费者，异步消费并缓存到队列
 * 2. 测试方法直接获取缓存的消息（无 Race Condition）
 * 3. Thread-safe 消息队列
 */
public class KafkaTestDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(KafkaTestDatabaseManager.class);

    private final String mysqlUrl;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final String mysqlDriver;
    private final String kafkaBootstrapServers;
    private final String kafkaGroupId;

    private final BlockingQueue<CacheMessage> messageQueue = new LinkedBlockingQueue<>();
    private Thread consumerThread;
    private volatile boolean running = false;
    private KafkaConsumer<String, Object> consumer;

    public KafkaTestDatabaseManager(String mysqlUrl, String mysqlUsername, String mysqlPassword, String mysqlDriver, 
                                   String kafkaBootstrapServers, String kafkaGroupId) {
        this.mysqlUrl = mysqlUrl;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.mysqlDriver = mysqlDriver;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaGroupId = kafkaGroupId;
    }

    public void executeUpdate(String sql) throws Exception {
        logger.info("Executing SQL: {}", sql);
        Class.forName(mysqlDriver);
        java.sql.Connection conn = java.sql.DriverManager.getConnection(mysqlUrl, mysqlUsername, mysqlPassword);
        try {
            conn.createStatement().execute(sql);
            logger.info("SQL 执行成功");
        } finally {
            conn.close();
        }
    }

    public boolean databaseExists(String database) throws Exception {
        String checkSql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = '" + database + "')";
        Long result = executeQuery(checkSql);
        return result != null && result > 0;
    }

    public void createDatabaseIfNotExists(String database) throws Exception {
        if (!databaseExists(database)) {
            logger.info("创建 database: {}", database);
            executeUpdate("CREATE DATABASE IF NOT EXISTS " + database);
            logger.info("Database 已创建");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T executeQuery(String sql) throws Exception {
        Class.forName(mysqlDriver);
        java.sql.Connection conn = java.sql.DriverManager.getConnection(mysqlUrl, mysqlUsername, mysqlPassword);
        try {
            java.sql.ResultSet rs = conn.createStatement().executeQuery(sql);
            if (rs.next()) {
                return (T) rs.getObject(1);
            }
            return null;
        } finally {
            conn.close();
        }
    }

    public void startConsumerAsync(String topic) throws Exception {
        cleanupConsumer();
        
        running = true;
        messageQueue.clear();

        consumerThread = new Thread(() -> {
            consumer = createConsumer();
            try {
                consumer.subscribe(Collections.singletonList(topic));
                logger.info("Kafka 消费者已启动，等待 topic: {}", topic);

                while (running) {
                    try {
                        java.util.Iterator<ConsumerRecord<String, Object>> it = consumer.poll(100).iterator();
                        while (it.hasNext()) {
                            ConsumerRecord<String, Object> record = it.next();
                            CacheMessage msg = convertMessage(record);
                            messageQueue.put(msg);
                            logger.debug("消息已缓存：topic={}, offset={}", record.topic(), record.offset());
                        }
                    } catch (InterruptedException e) {
                        if (running) {
                            Thread.currentThread().interrupt();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("消费者异常：{}", e.getMessage());
            } finally {
                if (consumer != null) {
                    consumer.close();
                }
            }
            logger.info("Kafka 消费者已停止");
        }, "kafka-consumer-" + topic.hashCode());

        consumerThread.start();
        logger.info("消费者线程已启动：{}", consumerThread.getName());
    }

    public CacheMessage getMessage(String topic, long timeoutMs) throws Exception {
        return messageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void cleanup() {
        cleanupConsumer();
        messageQueue.clear();
        logger.info("消费者资源已清理");
    }

    private void cleanupConsumer() {
        if (consumerThread != null) {
            running = false;
            consumerThread.interrupt();
            try {
                consumerThread.join(2000);
            } catch (InterruptedException e) {
                logger.warn("消费者线程中断超时");
            }
            consumerThread = null;
        }
        running = false;
    }

    private KafkaConsumer<String, Object> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapServers);
        props.put("group.id", kafkaGroupId + "-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.dbsyncer.connector.kafka.serialization.JsonToMapDeserializer");
        props.put("auto.offset.reset", "latest");
        props.put("enable.auto.commit", false);
        return new KafkaConsumer<>(props);
    }

    private CacheMessage convertMessage(ConsumerRecord<String, Object> record) {
        CacheMessage msg = new CacheMessage();
        msg.key = record.key();
        msg.topic = record.topic();
        msg.partition = record.partition();
        msg.offset = record.offset();
        msg.value = record.value();
        return msg;
    }

    public void cleanupTestTopics() {
        logger.info("清理测试 topics（仅打印日志，实际删除需手动）");
    }

    public static class CacheMessage {
        public String key;
        public String topic;
        public int partition;
        public Long offset;
        public Object value;

        @Override
        public String toString() {
            return "CacheMessage{topic='" + topic + "' partition=" + partition + " offset=" + offset + " value=" + value + "}";
        }
    }
}
