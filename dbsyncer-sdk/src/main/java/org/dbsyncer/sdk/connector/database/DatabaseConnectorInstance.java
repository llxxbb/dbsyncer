/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * 数据库连接器实例，使用 HikariCP 管理连接池。
 *
 * <p>HikariCP 自动处理连接的健康检测和故障驱逐：
 * 当连接上发生 SQLException（如 SocketTimeoutException）时，
 * HikariCP 的 ProxyConnection 会自动标记连接为 broken，
 * close() 时销毁而非还回池，确保重试时获取到新连接。</p>
 */
public class DatabaseConnectorInstance implements ConnectorInstance<DatabaseConfig, Connection> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private DatabaseConfig config;
    private HikariDataSource dataSource;

    public DatabaseConnectorInstance(DatabaseConfig config) {
        this.config = config;
        this.dataSource = createDataSource(config);
    }

    public <T> T execute(HandleCallback callback) throws Exception {
        Connection connection = null;
        try {
            connection = getConnection();
            return (T) callback.apply(new DatabaseTemplate(connection));
        } finally {
            // HikariCP 的 ProxyConnection.close() 会自动处理：
            // - 正常连接：还回池中
            // - broken 连接（发生过 SQLException）：销毁而非还回
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.debug("关闭连接时出现异常（可忽略）: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public String getServiceUrl() {
        return config.getUrl();
    }

    @Override
    public DatabaseConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public Connection getConnection() throws Exception {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * 重建数据源（用于动态更新 URL 等场景）。
     * 关闭旧连接池并创建新连接池。
     *
     * @param newUrl 新的 JDBC URL
     */
    public synchronized void recreateDataSource(String newUrl) {
        logger.info("重建连接池，新 URL: {}", newUrl);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        config.setUrl(newUrl);
        this.dataSource = createDataSource(config);
    }

    /**
     * 根据配置创建 HikariCP 数据源
     */
    private HikariDataSource createDataSource(DatabaseConfig cfg) {
        HikariConfig hikariConfig = new HikariConfig();
        // driverClassName 可为空，HikariCP 会从 JDBC URL 自动推断驱动类
        if (cfg.getDriverClassName() != null && !cfg.getDriverClassName().isEmpty()) {
            hikariConfig.setDriverClassName(cfg.getDriverClassName());
        }
        hikariConfig.setJdbcUrl(cfg.getUrl());
        hikariConfig.setUsername(cfg.getUsername());
        hikariConfig.setPassword(cfg.getPassword());

        // 连接池大小
        hikariConfig.setMaximumPoolSize(cfg.getMaxActive());
        hikariConfig.setMinimumIdle(1);

        // 连接生命周期（对应原 keepAlive 语义）
        // maxLifetime 必须大于 30 秒（HikariCP 最低要求）
        long maxLifetimeMs = Math.max(cfg.getKeepAlive(), 30000L);
        hikariConfig.setMaxLifetime(maxLifetimeMs);

        // 获取连接超时（默认 30 秒）
        hikariConfig.setConnectionTimeout(30000);

        // 空闲连接超时（默认 10 分钟）
        hikariConfig.setIdleTimeout(600000);

        // 连接有效性检测（使用 JDBC4 的 isValid，无需 connectionTestQuery）
        // HikariCP 默认在 getConnection 时检测连接有效性

        // 连接泄漏检测（开发/调试时非常有用）
        hikariConfig.setLeakDetectionThreshold(60000);

        // 池名称（便于日志识别）
        String poolName = "dbsyncer-" + sanitizePoolName(cfg.getUrl());
        hikariConfig.setPoolName(poolName);

        try {
            return new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            throw new SdkException("创建 HikariCP 连接池失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理 URL 用于池名称（去除特殊字符，截取关键部分）
     */
    private String sanitizePoolName(String url) {
        if (url == null) {
            return "unknown";
        }
        // 去除 jdbc: 前缀和参数
        String name = url.replaceAll("^jdbc:", "");
        int paramIdx = name.indexOf('?');
        if (paramIdx > 0) {
            name = name.substring(0, paramIdx);
        }
        // 截取前 40 个字符
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }


}
