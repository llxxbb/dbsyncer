/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.database.ds.SimpleConnection;
import org.dbsyncer.sdk.connector.database.ds.SimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnectorInstance implements ConnectorInstance<DatabaseConfig, Connection> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private DatabaseConfig config;
    private final SimpleDataSource dataSource;

    public DatabaseConnectorInstance(DatabaseConfig config) {
        this.config = config;
        this.dataSource = new SimpleDataSource(config.getDriverClassName(), config.getUrl(), config.getUsername(), config.getPassword(), config.getMaxActive(), config.getKeepAlive());
    }

    /**
     * 执行数据库操作
     *
     * <p>关键设计：当操作过程中发生连接级别的异常（如 SocketTimeoutException）时，
     * 直接销毁连接而不是还回连接池。这样可以防止重试机制复用一个已经损坏的连接，
     * 避免"每次重试都超时"的问题。</p>
     */
    public <T> T execute(HandleCallback callback) throws Exception {
        Connection connection = null;
        boolean broken = false;
        try {
            connection = getConnection();
            return (T) callback.apply(new DatabaseTemplate((SimpleConnection) connection));
        } catch (Exception e) {
            // 检测连接是否已损坏（SocketTimeout、网络异常等）
            // 损坏的连接不能还回连接池，否则重试会复用坏连接导致连续失败
            broken = isConnectionBroken(e);
            if (broken && connection != null) {
                logger.warn("检测到连接异常，销毁连接而非回收: {}", e.getMessage());
            }
            throw e;
        } finally {
            if (broken) {
                // 连接已损坏，直接销毁（关闭底层连接 + activeNum--），不还回池
                if (connection != null) {
                    dataSource.destroy((SimpleConnection) connection);
                }
            } else {
                dataSource.close(connection);
            }
        }
    }

    /**
     * 检测异常是否表明连接已损坏（不可复用）
     *
     * <p>遍历异常链，检查是否存在以下类型的异常：</p>
     * <ul>
     *   <li>{@link SocketTimeoutException} - Socket 读超时，TDS 协议状态可能不同步</li>
     *   <li>{@link SocketException} - 底层网络错误，连接已断开</li>
     * </ul>
     */
    private boolean isConnectionBroken(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof SocketException) {
                return true;
            }
            // 也检查 SQLException 的 error code（部分驱动用 error code 表示连接断开）
            if (cause instanceof SQLException) {
                String msg = cause.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("socket closed") || lower.contains("connection reset")
                            || lower.contains("broken pipe") || lower.contains("connection refused")
                            || lower.contains("no suitable host")) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        return false;
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
        dataSource.close();
    }

    public SimpleDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}