/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.cdc;

import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.network.ServerException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.mysql.MySQLException;
import org.dbsyncer.connector.mysql.binlog.BinaryLogClient;
import org.dbsyncer.connector.mysql.binlog.BinaryLogRemoteClient;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.listener.AbstractDatabaseListener;
import org.dbsyncer.sdk.listener.event.DDLChangedEvent;
import org.dbsyncer.sdk.listener.event.RowChangedEvent;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.util.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.regex.Pattern.compile;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2022-05-28 22:02
 */
public class MySQLListener extends AbstractDatabaseListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String BINLOG_FILENAME = "fileName";
    private final String BINLOG_POSITION = "position";
    private final String BINLOG_GTID = "gtid";
    // 表名 -> MetaInfo 映射（大小写不敏感），用于获取列名
    private Map<String, MetaInfo> sourceTableMap;
    // 大小写不敏感的过滤表集合
    private Set<String> filterTableLowerCache;
    private BinaryLogClient client;
    private String database;
    private final Lock connectLock = new ReentrantLock();

    @Override
    public void start() throws Exception {
        try {
            connectLock.lock();
            if (client != null && client.isConnected()) {
                logger.error("MySQLExtractor is already started");
                return;
            }
            run();
        } catch (Exception e) {
            logger.error("启动失败:{}", e.getMessage());
            throw new MySQLException(e);
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    public void init() {
        super.init();
        // 构建表名 -> MetaInfo 映射（大小写不敏感）
        sourceTableMap = new LinkedCaseInsensitiveMap<>();
        if (sourceTable != null) {
            for (Table table : sourceTable) {
                if (table instanceof MetaInfo) {
                    sourceTableMap.put(table.getName(), (MetaInfo) table);
                }
            }
        }
        // 构建大小写不敏感的过滤表集合
        rebuildFilterTableLowerCache();
    }

    /**
     * 重建大小写不敏感的过滤表集合
     * 用于 DQL 场景：postProcessDqlBeforeInitialization() 会重建 filterTable
     */
    protected void rebuildFilterTableLowerCache() {
        filterTableLowerCache = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (filterTable != null) {
            filterTableLowerCache.addAll(filterTable);
        }
    }

    @Override
    public void close() {
        try {
            connectLock.lock();
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            super.close();
        } catch (Exception e) {
            logger.error("关闭失败:{}", e.getMessage());
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    public void refreshEvent(ChangedOffset offset) {
        if (offset == null) {
            return;
        }
        Assert.notNull(offset.getNextFileName(), "offset.nextFileName is null") ;
        Assert.notNull(offset.getPosition(), "offset.position is null");
        refreshSnapshot(offset.getNextFileName(), (Long) offset.getPosition(), offset.getGtid());
        super.refreshEvent(offset);
    }

    private void run() throws Exception {
        final DatabaseConfig config = getConnectorInstance().getConfig();
        if (StringUtil.isBlank(config.getUrl())) {
            throw new MySQLException("url is invalid");
        }
        database = DatabaseUtil.getDatabaseName(config.getUrl());
        List<Host> cluster = readNodes(config.getUrl());
        Assert.notEmpty(cluster, "MySQL连接地址有误.");

        int MASTER = 0;
        final Host host = cluster.get(MASTER);
        final String username = config.getUsername();
        final String password = config.getPassword();
        client = new BinaryLogRemoteClient(host.getIp(), host.getPort(), username, password);

        // 模式选择：snapshot 中有 gtid 且非空则使用 GTID 模式，否则使用 fileName+position 模式
        String gtid = snapshot.get(BINLOG_GTID);
        if (!StringUtil.isBlank(gtid)) {
            client.setGtidSet(gtid);
            logger.info("使用 GTID 模式连接: {}", gtid);
        } else {
            client.setBinlogFilename(snapshot.get(BINLOG_FILENAME));
            boolean containsPos = snapshot.containsKey(BINLOG_POSITION);
            client.setBinlogPosition(containsPos ? Long.parseLong(snapshot.get(BINLOG_POSITION)) : 0);
            logger.info("使用 fileName+position 模式连接: {}:{}", client.getBinlogFilename(), client.getBinlogPosition());
        }

        client.registerEventListener(new InnerEventListener());
        client.registerLifecycleListener(new InnerLifecycleListener());

        client.connect();
    }

    private List<Host> readNodes(String url) {
        Matcher matcher = compile("(//)(?!(/)).+?(/)").matcher(url);
        if (matcher.find()) {
            url = matcher.group(0);
        }
        url = StringUtil.replace(url, "/", "");

        List<Host> cluster = new ArrayList<>();
        String[] arr = StringUtil.split(url, StringUtil.COMMA);
        for (String s : arr) {
            String[] host = StringUtil.split(s, ":");
            if (2 == host.length) {
                cluster.add(new Host(host[0], Integer.parseInt(host[1])));
            }
        }
        return cluster;
    }

    private void refreshSnapshot(String binlogFilename, long nextPosition, String gtidSet) {
        snapshot.put(BINLOG_FILENAME, binlogFilename);
        snapshot.put(BINLOG_POSITION, String.valueOf(nextPosition));
        // GTID 不可用时写空字符串，防止重启时读到过期 GTID 值
        snapshot.put(BINLOG_GTID, gtidSet != null ? gtidSet : "");
    }

    static final class Host {
        private final String ip;
        private final int port;

        public Host(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }
    }

    final class InnerLifecycleListener implements BinaryLogRemoteClient.LifecycleListener {

        @Override
        public void onConnect(BinaryLogRemoteClient client) {
        }

        @Override
        public void onException(BinaryLogRemoteClient client, Exception e) {
            if (!client.isConnected()) {
                return;
            }
            if (e instanceof ServerException) {
                ServerException serverException = (ServerException) e;
                if (serverException.getErrorCode() == 1236) {
                    logger.error(e.getMessage());
                    errorEvent(new MySQLException(e.getMessage()));
                    close();
                    return;
                }
            }
            String errorMsg = String.format("MySQL binlog监听异常，binlog: %s:%s, error: %s",
                    client.getBinlogFilename(), client.getBinlogPosition(), e.getMessage());
            logger.error(errorMsg, e);
            errorEvent(new MySQLException(errorMsg, e));
        }

        @Override
        public void onDisconnect(BinaryLogRemoteClient client) {
        }

    }

    final class InnerEventListener implements BinaryLogRemoteClient.EventListener {

        /**
         * 只处理非dbs写入事件（单线程消费，不存在并发竞争）
         */
        private boolean notUniqueCodeEvent = true;

        /**
         * 上一个事务的 XID 位置（用于 ROW 事件携带）
         * 单线程消费，不存在并发竞争
         */
        private String lastXIDFileName = null;
        private Long lastXIDPosition = null;
        /**
         * 上一个事务完成时的完整 GTID set（从 XID 事件时 client.getGtidSet() 获取）
         * 随事件传递给消费线程，避免消费线程读共享状态导致 GTID 超前
         */
        private String lastXIDGtidSet = null;

        @Override
        public void onEvent(Event event) {
            // ROTATE > FORMAT_DESCRIPTION > TABLE_MAP > WRITE_ROWS > UPDATE_ROWS > DELETE_ROWS > XID
            EventHeader header = event.getHeader();
            
            if (header.getEventType() == EventType.XID) {
                // 记录 XID 位置（用于后续 ROW 事件携带）
                EventHeaderV4 eventHeaderV4 = (EventHeaderV4) header;
                if (lastXIDFileName == null) {
                    lastXIDFileName = client.getBinlogFilename();
                }
                lastXIDPosition = eventHeaderV4.getNextPosition();
                // 捕获当前完整 GTID set（此时 GtidSetManager 已提交当前事务 GTID）
                lastXIDGtidSet = client.getGtidSet();

                if (!hasPendingTaskData()) {
                    // 没有任务数据在处理：非任务事件的 XID，正常更新 snapshot 和 pendingSnapshot
                    ChangedOffset offset = new ChangedOffset();
                    offset.setNextFileName(lastXIDFileName);
                    offset.setPosition(lastXIDPosition);
                    offset.setGtid(lastXIDGtidSet);
                    refreshEvent(offset);
                }
                return;
            }

            if (header.getEventType() == EventType.TABLE_MAP) {
                return;
            }

            if (header.getEventType() == EventType.ROWS_QUERY) {
                RowsQueryEventData data = event.getData();
                notUniqueCodeEvent = isNotUniqueCodeEvent(data.getQuery());
                return;
            }

            if (notUniqueCodeEvent && EventType.isUpdate(header.getEventType())) {
                UpdateRowsEventData data = event.getData();
                if (isFilterTable(data.getTableId())) {
                    long tableId = data.getTableId();
                    List<String> columnNames = getColumnNames(tableId);
                    data.getRows().forEach(m -> {
                        List<Object> before = rowDataToList(m.getKey(), columnNames);
                        List<Object> after = rowDataToList(m.getValue(), columnNames);
                        RowChangedEvent rowEvent = new RowChangedEvent(getTableName(tableId), ConnectorConstant.OPERTION_UPDATE, after, before, lastXIDFileName, lastXIDPosition, columnNames);
                        rowEvent.setGtid(lastXIDGtidSet);
                        trySendEvent(rowEvent);
                    });
                }
                return;
            }
            if (notUniqueCodeEvent && EventType.isWrite(header.getEventType())) {
                WriteRowsEventData data = event.getData();
                if (isFilterTable(data.getTableId())) {
                    long tableId = data.getTableId();
                    List<String> columnNames = getColumnNames(tableId);
                    data.getRows().forEach(m -> {
                        List<Object> after = Stream.of(m).collect(Collectors.toList());
                        RowChangedEvent rowEvent = new RowChangedEvent(getTableName(tableId), ConnectorConstant.OPERTION_INSERT, after, after, lastXIDFileName, lastXIDPosition, columnNames);
                        rowEvent.setGtid(lastXIDGtidSet);
                        trySendEvent(rowEvent);
                    });
                }
                return;
            }
            if (notUniqueCodeEvent && EventType.isDelete(header.getEventType())) {
                DeleteRowsEventData data = event.getData();
                if (isFilterTable(data.getTableId())) {
                    long tableId = data.getTableId();
                    List<String> columnNames = getColumnNames(tableId);
                    data.getRows().forEach(m -> {
                        List<Object> beforeData = Stream.of(m).collect(Collectors.toList());
                        RowChangedEvent rowEvent = new RowChangedEvent(getTableName(tableId), ConnectorConstant.OPERTION_DELETE, beforeData, beforeData, lastXIDFileName, lastXIDPosition, columnNames);
                        rowEvent.setGtid(lastXIDGtidSet);
                        trySendEvent(rowEvent);
                    });
                }
                return;
            }

            if (EventType.QUERY == header.getEventType()) {
                parseDDL(event.getData());
                return;
            }

            if (header.getEventType() == EventType.ROTATE) {
                RotateEventData rotateData = (RotateEventData) event.getData();
                lastXIDFileName = rotateData.getBinlogFilename();
                lastXIDPosition = rotateData.getBinlogPosition();
            }
        }

        private void parseDDL(QueryEventData data) {
            if (isNotUniqueCodeEvent(data.getSql())) {
                Statement statement = parseStatement(data.getSql());
                if (statement instanceof Alter) {
                    parseAlter(data, (Alter) statement);
                }
            }
        }

        private Statement parseStatement(String sql) {
            try {
                // skip BEGIN
                if (!StringUtil.equalsIgnoreCase("BEGIN", sql)) {
                    return CCJSqlParserUtil.parse(sql);
                }
            } catch (JSQLParserException e) {
                if (sql.startsWith("SAVEPOINT")) {
                    return null;
                }
                logger.debug("不支持的ddl:{}", sql);
            }
            return null;
        }

        private void parseAlter(QueryEventData data, Alter alter) {
            // ALTER TABLE `test`.`my_user` MODIFY COLUMN `name` varchar(128) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL AFTER `id`
            String tableName = StringUtil.replace(alter.getTable().getName(), StringUtil.BACK_QUOTE, StringUtil.EMPTY);
            // databaseName 的取值不能为 QueryEventData#getDatabase, getDatabase 获取的是执行 SQL 语句时所在的数据库上下文,
            // 不是 alter 语句修改的表所在的数据库
            // 依次执行下面两条语句 use db1; alter table db2.my_user add column name varchar(128);
            // data.getDatabase() 的值为 db1, 不是 db2, 必须从 alter 中获取 databaseName
            String databaseName = alter.getTable().getSchemaName();
            // alter 中没有 databaseName 说明是 alter table my_user xxx 这种格式, 直接在上下文中获取即可
            if (StringUtil.isBlank(databaseName)) {
                databaseName = data.getDatabase();
            }
            databaseName = StringUtil.replace(databaseName, StringUtil.BACK_QUOTE, StringUtil.EMPTY);
            if (isFilterTable(databaseName, tableName)) {
                logger.info("捕获到 DDL 事件: database={}, table={}, sql={}", databaseName, tableName, data.getSql());
                DDLChangedEvent ddlEvent = new DDLChangedEvent(tableName, ConnectorConstant.OPERTION_ALTER,
                        data.getSql(), lastXIDFileName, lastXIDPosition);
                ddlEvent.setGtid(lastXIDGtidSet);
                trySendEvent(ddlEvent);
            }
        }

        private String getTableName(long tableId) {
            return client.getTableMapEventByTableId().get(tableId).getTable();
        }

        /**
         * 获取表的列名列表（按数据顺序）
         * 从 MetaInfo 的派生缓存获取，无需查询数据库
         */
        private List<String> getColumnNames(long tableId) {
            TableMapEventData tableMap = client.getTableMapEventByTableId().get(tableId);
            if (tableMap == null) {
                return null;
            }
            String tableName = tableMap.getTable();
            MetaInfo metaInfo = sourceTableMap.get(tableName);
            if (metaInfo == null) {
                logger.warn("未找到表的 MetaInfo: tableName={}, tableId={}", tableName, tableId);
                return null;
            }
            return metaInfo.getColumnNames();
        }

        private boolean isFilterTable(long tableId) {
            final TableMapEventData tableMap = client.getTableMapEventByTableId().get(tableId);
            return isFilterTable(tableMap.getDatabase(), tableMap.getTable());
        }

        private boolean isFilterTable(String dbName, String tableName) {
            return StringUtil.equalsIgnoreCase(database, dbName) && filterTableLowerCache.contains(tableName);
        }

        private boolean isNotUniqueCodeEvent(String sql) {
            return !StringUtil.startsWith(sql, "/*dbs*/");
        }

        private List<Object> rowDataToList(Object rowData, List<String> columnNames) {
            if (rowData == null) {
                return new ArrayList<>();
            }
            if (rowData instanceof Object[]) {
                return new ArrayList<>(Arrays.asList((Object[]) rowData));
            }
            if (rowData instanceof Map) {
                List<Object> values = new ArrayList<>();
                for (Object value : ((Map<?, ?>) rowData).values()) {
                    values.add(value);
                }
                return values;
            }
            List<Object> result = new ArrayList<>();
            result.add(rowData);
            return result;
        }

    }
}