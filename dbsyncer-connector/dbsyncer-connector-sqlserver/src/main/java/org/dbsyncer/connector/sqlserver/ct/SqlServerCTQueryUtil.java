package org.dbsyncer.connector.sqlserver.ct;

import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQL Server Change Tracking 查询工具类
 * 提供统一的数据库查询方法，支持结果集映射、参数预编译、死锁重试等功能
 *
 * @author DBSyncer
 * @version 1.0.0
 */
public class SqlServerCTQueryUtil {

    private static final Logger logger = LoggerFactory.getLogger(SqlServerCTQueryUtil.class);

    private final DatabaseConnectorInstance instance;

    /**
     * 构造函数
     *
     * @param instance 数据库连接器实例
     */
    public SqlServerCTQueryUtil(DatabaseConnectorInstance instance) {
        this.instance = instance;
    }

    /**
     * ResultSet 映射器接口
     */
    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T apply(ResultSet rs) throws SQLException;
    }

    /**
     * Statement 预处理器接口
     */
    @FunctionalInterface
    public interface StatementPreparer {
        void accept(PreparedStatement statement) throws SQLException;
    }

    /**
     * 查询并映射单条结果
     *
     * @param sql    SQL 语句
     * @param mapper 结果集映射器
     * @param <T>    返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T queryAndMap(String sql, ResultSetMapper<T> mapper) throws Exception {
        return queryAndMap(sql, null, mapper);
    }

    /**
     * 查询并映射单条结果（带参数预编译）
     *
     * @param sql              SQL 语句
     * @param statementPreparer Statement 预处理器
     * @param mapper           结果集映射器
     * @param <T>              返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T queryAndMap(String sql, StatementPreparer statementPreparer, ResultSetMapper<T> mapper) throws Exception {
        return query(sql, statementPreparer, (rs) -> {
            rs.next();
            return mapper.apply(rs);
        });
    }

    /**
     * 查询并映射结果列表
     *
     * @param sql    SQL 语句
     * @param mapper 结果集映射器（需要自行处理 ResultSet 的遍历）
     * @param <T>    返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T queryAndMapList(String sql, ResultSetMapper<T> mapper) throws Exception {
        return queryAndMapList(sql, null, mapper);
    }

    /**
     * 查询并映射结果列表（带参数预编译）
     *
     * @param sql              SQL 语句
     * @param statementPreparer Statement 预处理器
     * @param mapper           结果集映射器（需要自行处理 ResultSet 的遍历）
     * @param <T>              返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T queryAndMapList(String sql, StatementPreparer statementPreparer, ResultSetMapper<T> mapper) throws Exception {
        return query(sql, statementPreparer, mapper);
    }

    /**
     * 执行查询的核心方法
     *
     * @param preparedQuerySql 预编译的 SQL 语句
     * @param statementPreparer Statement 预处理器
     * @param mapper           结果集映射器
     * @param <T>              返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T query(String preparedQuerySql, StatementPreparer statementPreparer, ResultSetMapper<T> mapper) throws Exception {
        // 输出 SQL 语句用于调试
        logger.debug("执行查询 SQL: {}", preparedQuerySql);
        Object execute = instance.execute(databaseTemplate -> {
            PreparedStatement ps = null;
            ResultSet rs = null;
            T apply = null;
            try {
                ps = databaseTemplate.getSimpleConnection().prepareStatement(preparedQuerySql);
                if (null != statementPreparer) {
                    statementPreparer.accept(ps);
                }
                rs = ps.executeQuery();
                apply = mapper.apply(rs);
            } catch (Exception e) {
                logger.error("查询失败，SQL: {}, 错误: {}", preparedQuerySql, e.getMessage(), e);
                throw e;
            } finally {
                close(rs);
                close(ps);
            }
            return apply;
        });
        return (T) execute;
    }

    /**
     * 使用 READ UNCOMMITTED 隔离级别查询表元信息，避免死锁
     * 当执行 DDL 操作时，查询 INFORMATION_SCHEMA 可能会与 DDL 操作产生死锁
     * 使用 READ UNCOMMITTED 隔离级别可以避免等待锁，减少死锁概率
     *
     * @param preparedQuerySql  预编译的 SQL 语句
     * @param statementPreparer Statement 预处理器
     * @param mapper            结果集映射器
     * @param <T>               返回类型
     * @return 映射后的结果
     * @throws Exception 查询异常
     */
    public <T> T queryWithReadUncommitted(String preparedQuerySql, StatementPreparer statementPreparer, ResultSetMapper<T> mapper) throws Exception {
        return (T) instance.execute(databaseTemplate -> {
            Connection conn = databaseTemplate.getSimpleConnection();
            int originalIsolation = conn.getTransactionIsolation();
            PreparedStatement ps = null;
            ResultSet rs = null;
            T apply = null;
            try {
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                ps = conn.prepareStatement(preparedQuerySql);
                if (null != statementPreparer) {
                    statementPreparer.accept(ps);
                }
                rs = ps.executeQuery();
                apply = mapper.apply(rs);
            } finally {
                try {
                    conn.setTransactionIsolation(originalIsolation);
                } catch (SQLException e) {
                    logger.error("恢复事务隔离级别失败: {}", e.getMessage(), e);
                    throw new RuntimeException("恢复事务隔离级别失败", e);
                } finally {
                    close(rs);
                    close(ps);
                }
            }
            return apply;
        });
    }

    /**
     * 关闭资源
     *
     * @param closeable 可关闭的资源
     */
    private void close(AutoCloseable closeable) {
        if (null != closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }
}
