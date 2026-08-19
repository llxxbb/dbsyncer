/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import com.github.shyiko.mysql.binlog.GtidSet;
import com.github.shyiko.mysql.binlog.MariadbGtidSet;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.*;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;
import com.github.shyiko.mysql.binlog.network.*;
import com.github.shyiko.mysql.binlog.network.protocol.*;
import com.github.shyiko.mysql.binlog.network.protocol.command.*;
import org.dbsyncer.common.retry.RetryAfter;
import org.dbsyncer.common.retry.RetryInterceptor;
import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.common.retry.TerminationMode;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.mysql.deserializer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * MySQL binlog 远程客户端。
 * <p>基于 mysql-binlog-connector-java 0.30.1（com.zendesk，Apache-2.0）的 BinaryLogClient 定制：
 * <ul>
 *     <li>持续观察并累积 GTID，重连时自动在 GTID / fileName+position 模式间切换（ADR-0026）</li>
 *     <li>keepalive 线程检测断连并指数退避自动重连</li>
 *     <li>MySQL 重启导致位点失效（bogus data，错误码 1236）时重置 binlog 位置恢复</li>
 *     <li>适配 MariaDB 协议与 GTID 格式</li>
 * </ul>
 */
public class BinaryLogRemoteClient implements BinaryLogClient {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final SSLSocketFactory DEFAULT_REQUIRED_SSL_MODE_SOCKET_FACTORY = new DefaultSSLSocketFactory() {

        @Override
        protected void initSSLContext(SSLContext sc) throws GeneralSecurityException {
            sc.init(null, new TrustManager[]{
                    new X509TrustManager() {

                        @Override
                        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            }, null);
        }
    };
    private static final SSLSocketFactory DEFAULT_VERIFY_CA_SSL_MODE_SOCKET_FACTORY = new DefaultSSLSocketFactory();

    // https://dev.mysql.com/doc/internals/en/sending-more-than-16mbyte.html
    private static final int MAX_PACKET_LENGTH = 16777215;

    // 版本号 8.4.0 的编码值（见 parseVersion）。MySQL 8.4 起移除 SHOW MASTER STATUS，需改用 SHOW BINARY LOG STATUS
    private static final long MYSQL_VERSION_8_4 = 8400000000L;

    // socket 连接超时（毫秒）
    private static final int CONNECT_TIMEOUT_MS = 3000;

    // keepalive 重连重试的初始间隔与指数退避上限（毫秒）
    private static final long KEEPALIVE_RETRY_INITIAL_MS = 500L;
    private static final long KEEPALIVE_RETRY_MAX_MS = 30000L;

    // MariaDB GTID 集合格式：DOMAIN_ID-SERVER_ID-SEQUENCE[, ...]
    private static final Pattern MARIA_GTID_SET_PATTERN =
            Pattern.compile("^\\d{1,10}-\\d{1,10}-\\d{1,20}(\\s*,\\s*\\d{1,10}-\\d{1,10}-\\d{1,20})*$");

    private final String hostname;
    private final int port;
    private final String schema;
    private final String username;
    private final String password;
    private SSLMode sslMode = SSLMode.DISABLED;

    private EventDeserializer eventDeserializer;
    private Map<Long, TableMapEventData> tableMapEventByTableId;
    private long serverId = 65535L;
    private volatile String binlogFilename;
    private volatile long binlogPosition = 4;
    private volatile long connectionId;
    private volatile PacketChannel channel;
    private volatile boolean connected;
    private volatile boolean connectedError;
    private Thread worker;
    private Thread keepAlive;

    private final Lock connectLock = new ReentrantLock();
    private final Object gtidSetAccessLock = new Object();
    private GtidSet gtidSet;
    private String gtid;
    private boolean tx;
    private Boolean isMariaDB;

    private final List<BinaryLogRemoteClient.EventListener> eventListeners = new CopyOnWriteArrayList<>();
    private final List<BinaryLogRemoteClient.LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    /**
     * Alias for BinaryLogRemoteClient(hostname, port, &lt;no schema&gt; = null, username, password).
     *
     * @see BinaryLogRemoteClient#BinaryLogRemoteClient(String, int, String, String, String, long)
     */
    public BinaryLogRemoteClient(String hostname, int port, String username, String password) {
        this(hostname, port, null, username, password, 0L);
    }

    /**
     * @param hostname mysql server hostname
     * @param port     mysql server port
     * @param schema   database name, nullable. Note that this parameter has nothing to do with event filtering. It's used only during the
     *                 authentication.
     * @param username login name
     * @param password password
     * @param serverId serverId
     */
    public BinaryLogRemoteClient(String hostname, int port, String schema, String username, String password, long serverId) {
        this.hostname = hostname;
        this.port = port;
        this.schema = schema;
        this.username = username;
        this.password = password;
        this.serverId = resolveServerId(serverId);
        logger.info("MySQL Binlog Client serverId: {} (auto={})", this.serverId, serverId == 0);
    }

    @Override
    public void connect() throws Exception {
        try {
            connectLock.lock();
            if (connected) {
                throw new IllegalStateException("BinaryLogRemoteClient is already connected");
            }
            initEventDeserializer();
            try {
                openChannel();
                connected = true;
                // new keepalive thread
                spawnKeepAliveThread();

                // dump binary log
                requestBinaryLogStream();
                ensureEventDeserializerHasRequiredEDDs();

                // new listen thread
                spawnWorkerThread();
            } catch (Exception e) {
                // 连接失败统一回滚：停止 keepAlive、关闭 channel，避免线程与 socket 泄漏
                stopKeepAliveThread();
                connected = false;
                closeChannelQuietly();
                throw e;
            }
            lifecycleListeners.forEach(listener -> listener.onConnect(this));
        } finally {
            connectLock.unlock();
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (connected) {
            try {
                connectLock.lock();
                closeChannel(channel);
                connected = false;
                Thread workerToJoin = this.worker;
                Thread keepAliveToJoin = this.keepAlive;
                if (null != workerToJoin && !workerToJoin.isInterrupted()) {
                    workerToJoin.interrupt();
                }
                this.worker = null;
                if (null != keepAliveToJoin && !keepAliveToJoin.isInterrupted()) {
                    keepAliveToJoin.interrupt();
                }
                this.keepAlive = null;
                // 等待旧线程退出，避免新旧两代 worker 并存引发竞态
                joinQuietly(workerToJoin);
                joinQuietly(keepAliveToJoin);
                lifecycleListeners.forEach(listener -> listener.onDisconnect(this));
            } finally {
                connectLock.unlock();
            }
        }
    }

    /**
     * 等待线程退出（至多 3 秒）。跳过当前线程自身，避免 keepAlive 线程在重连路径中 self-join。
     */
    private void joinQuietly(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopKeepAliveThread() {
        if (null != this.keepAlive) {
            this.keepAlive.interrupt();
            this.keepAlive = null;
        }
    }

    private void closeChannelQuietly() {
        try {
            closeChannel(channel);
        } catch (IOException e) {
            logger.warn("Failed to close channel: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    @Override
    public void registerEventListener(BinaryLogRemoteClient.EventListener eventListener) {
        eventListeners.add(eventListener);
    }

    @Override
    public void registerLifecycleListener(BinaryLogRemoteClient.LifecycleListener lifecycleListener) {
        lifecycleListeners.add(lifecycleListener);
    }

    private String createClientId() {
        return hostname + ":" + port + "_" + connectionId;
    }

    private void openChannel() throws IOException {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(hostname, port), CONNECT_TIMEOUT_MS);
            // 启用 TCP keepalive 尽早发现死连接（半开）；禁用 Nagle 降低事件传输延迟
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            channel = new PacketChannel(socket);
            if (channel.getInputStream().peek() == -1) {
                throw new EOFException();
            }
        } catch (IOException e) {
            closeChannel(channel);
            throw new IOException("Failed to connect to MySQL on " + hostname + ":" + port + ". Please make sure it's running.", e);
        }
        GreetingPacket greetingPacket = receiveGreeting(channel);
        detectMariaDB(greetingPacket);
        tryUpgradeToSSL(greetingPacket);
        new Authenticator(greetingPacket, channel, schema, username, password).authenticate();
        channel.authenticationComplete();

        connectionId = greetingPacket.getThreadId();
        // 初始化 GTID set（用于观察或 GTID 模式）
        if (gtidSet == null) {
            synchronized (gtidSetAccessLock) {
                if (Boolean.TRUE.equals(isMariaDB)) {
                    gtidSet = new MariadbGtidSet("");
                } else {
                    gtidSet = new GtidSet("");
                }
            }
        }
        if (binlogFilename == null) {
            fetchBinlogFilenameAndPosition();
        }
        if (binlogPosition < 4) {
            logger.warn("Binary log position adjusted from {} to {}", binlogPosition, 4);
            binlogPosition = 4;
        }
        ChecksumType checksumType = fetchBinlogChecksum();
        if (checksumType != ChecksumType.NONE) {
            confirmSupportOfChecksum(channel, checksumType);
        }
        synchronized (gtidSetAccessLock) {
            String position = gtidSet != null ? gtidSet.toString() : binlogFilename + "/" + binlogPosition;
            logger.info("Connected to {}:{} at {} (sid:{}, cid:{})", hostname, port, position, serverId, connectionId);
        }
    }

    private void detectMariaDB(GreetingPacket packet) {
        String serverVersion = packet.getServerVersion();
        if (serverVersion == null) {
            return;
        }
        this.isMariaDB = StringUtil.containsIgnoreCase(serverVersion, "mariadb");
    }

    private boolean tryUpgradeToSSL(GreetingPacket greetingPacket) throws IOException {
        if (sslMode != SSLMode.DISABLED) {
            boolean serverSupportsSSL = (greetingPacket.getServerCapabilities() & ClientCapabilities.SSL) != 0;
            if (!serverSupportsSSL && (sslMode == SSLMode.REQUIRED || sslMode == SSLMode.VERIFY_CA ||
                    sslMode == SSLMode.VERIFY_IDENTITY)) {
                throw new IOException("MySQL server does not support SSL");
            }
            if (serverSupportsSSL) {
                SSLRequestCommand sslRequestCommand = new SSLRequestCommand();
                int collation = greetingPacket.getServerCollation();
                sslRequestCommand.setCollation(collation);
                channel.write(sslRequestCommand);
                SSLSocketFactory sslSocketFactory = sslMode == SSLMode.REQUIRED || sslMode == SSLMode.PREFERRED ?
                        DEFAULT_REQUIRED_SSL_MODE_SOCKET_FACTORY :
                        DEFAULT_VERIFY_CA_SSL_MODE_SOCKET_FACTORY;
                channel.upgradeToSSL(sslSocketFactory, sslMode == SSLMode.VERIFY_IDENTITY ? new TLSHostnameVerifier() : null);
                logger.info("SSL enabled");
                return true;
            }
        }
        return false;
    }

    private void listenForEventPackets(final PacketChannel channel) {
        ByteArrayInputStream inputStream = channel.getInputStream();
        long eventCount = 0;
        try {
            while (inputStream.peek() != -1) {
                // 检查连接状态和线程中断状态，如果已断开或中断则退出循环
                if (!connected || Thread.currentThread().isInterrupted()) {
                    break;
                }

                int packetLength = inputStream.readInteger(3);

                // 验证 packetLength 的合理性（MySQL packet 最大长度为 16MB-1）
                if (packetLength < 0 || packetLength > MAX_PACKET_LENGTH) {
                    logger.error("Invalid packet length detected: packetLength={}, binlogFilename={}, binlogPosition={}, eventCount={}",
                            packetLength, binlogFilename, binlogPosition, eventCount);
                    throw new IOException("Invalid packet length: " + packetLength + " (expected 0-" + MAX_PACKET_LENGTH + ")");
                }

                //noinspection ResultOfMethodCallIgnored
                // 1 byte for sequence
                inputStream.skip(1);
                int marker = inputStream.read();
                // 255 error
                if (marker == 0xFF) {
                    ErrorPacket errorPacket = new ErrorPacket(inputStream.read(packetLength - 1));
                    throw new ServerException(errorPacket.getErrorMessage(), errorPacket.getErrorCode(),
                            errorPacket.getSqlState());
                }
                // 关键修复：确保每个 packet 的数据都被完全隔离，避免流位置错乱
                // 问题：当 packetLength != MAX_PACKET_LENGTH 时，直接传递共享的 inputStream 给 nextEvent()
                // 如果 nextEvent() 没有完全消费掉 packetLength - 1 字节，会导致下一个 packet 读取位置错乱
                // 解决方案：始终使用独立的 ByteArrayInputStream 来隔离每个 packet 的数据
                int eventDataLength = packetLength - 1; // 减去已读取的 marker 字节
                byte[] eventData;
                if (packetLength == MAX_PACKET_LENGTH) {
                    eventData = readPacketSplitInChunks(inputStream, eventDataLength);
                } else {
                    eventData = inputStream.read(eventDataLength);
                    if (eventData == null || eventData.length != eventDataLength) {
                        logger.error("Failed to read complete event data: expected length={}, actual length={}, packetLength={}, binlogFilename={}, binlogPosition={}, eventCount={}",
                                eventDataLength, (eventData == null ? 0 : eventData.length), packetLength, binlogFilename, binlogPosition, eventCount);
                        throw new IOException("Failed to read complete event data: expected length=" + eventDataLength +
                                ", actual length=" + (eventData == null ? 0 : eventData.length) + ", packetLength=" + packetLength);
                    }
                }

                Event event;
                try {
                    event = eventDeserializer.nextEvent(new ByteArrayInputStream(eventData));
                } catch (Exception e) {
                    // fail-fast：任何反序列化失败都不静默跳过。
                    // 例如缺少 TableMapEventData 时，其 ROWS 事件无法通过向后读取补齐，跳过即静默丢数据；
                    // 上抛后由 LifecycleListener.onException 决策（停任务/告警），优于隐性数据丢失。
                    int eventTypeByte = eventData.length > 4 ? (eventData[4] & 0xFF) : -1;
                    logger.error("Failed to deserialize event: eventTypeByte={}, eventDataLength={}, binlogFilename={}, binlogPosition={}, eventCount={}, eventData[0-50]={}",
                            eventTypeByte, eventData.length, binlogFilename, binlogPosition, eventCount,
                            eventData.length > 50 ? Arrays.toString(Arrays.copyOf(eventData, 50)) : Arrays.toString(eventData), e);
                    throw e;
                }

                if (event != null) {
                    eventCount++;
                    updateGtidSet(event);
                    notifyEventListeners(event);
                    updateClientBinlogFilenameAndPosition(event);
                    continue;
                }
                throw new EOFException("event data deserialization exception");
            }
        } catch (Exception e) {
            // 旧一代 worker（重连后已被替换）抛出的异常直接丢弃，避免对新连接误触发重连风暴
            // 日志由 notifyException 统一打印，此处不再重复
            if (!isStaleWorker(channel) && !Thread.currentThread().isInterrupted()) {
                notifyException(e);
            }
        }
    }

    /**
     * 判断本事件循环所属的连接是否已被替换：
     * worker 在创建时持有当时的 channel 引用，重连后 this.channel 指向新实例，
     * 旧 worker 的异常应丢弃，不能触发新连接的 notifyException。
     */
    private boolean isStaleWorker(PacketChannel workerChannel) {
        return !connected || workerChannel != this.channel;
    }

    private void ensureEventDeserializerHasRequiredEDDs() {
        ensureEventDataDeserializerIfPresent(EventType.ROTATE, RotateEventDataDeserializer.class);
        // Always ensure GTID-related deserializers (for observation and GTID mode)
        ensureEventDataDeserializerIfPresent(EventType.GTID, GtidEventDataDeserializer.class);
        ensureEventDataDeserializerIfPresent(EventType.QUERY, QueryEventDataDeserializer.class);
        ensureEventDataDeserializerIfPresent(EventType.ANNOTATE_ROWS, AnnotateRowsEventDataDeserializer.class);
        ensureEventDataDeserializerIfPresent(EventType.MARIADB_GTID, MariadbGtidEventDataDeserializer.class);
        ensureEventDataDeserializerIfPresent(EventType.MARIADB_GTID_LIST, MariadbGtidListEventDataDeserializer.class);
    }

    /**
     * 检查响应包是否为 MySQL 错误包（首字节 0xFF），是则抛出 ServerException。
     */
    private static void checkErrorPacket(byte[] packet) throws IOException {
        if (packet != null && packet.length > 0 && packet[0] == (byte) 0xFF /* error */) {
            ErrorPacket errorPacket = new ErrorPacket(Arrays.copyOfRange(packet, 1, packet.length));
            throw new ServerException(errorPacket.getErrorMessage(), errorPacket.getErrorCode(),
                    errorPacket.getSqlState());
        }
    }

    private static byte[] readPacketChecked(PacketChannel channel) throws IOException {
        byte[] packet = channel.read();
        if (packet == null || packet.length == 0) {
            throw new IOException("Empty packet received");
        }
        return packet;
    }

    private GreetingPacket receiveGreeting(final PacketChannel channel) throws IOException {
        byte[] initialHandshakePacket = readPacketChecked(channel);
        checkErrorPacket(initialHandshakePacket);
        return new GreetingPacket(initialHandshakePacket);
    }

    private void requestBinaryLogStream() throws IOException {
        if (Boolean.TRUE.equals(this.isMariaDB)) {
            requestBinaryLogStreamMaria(this.serverId);
            return;
        }

        requestBinaryLogStreamMysql(this.serverId);
    }

    private void requestBinaryLogStreamMysql(long serverId) throws IOException {
        Command dumpBinaryLogCommand;
        synchronized (gtidSetAccessLock) {
            if (gtidSet != null && gtidSet.toString().length() > 0) {
                dumpBinaryLogCommand = new DumpBinaryLogGtidCommand(serverId, "", 4, gtidSet);
            } else {
                dumpBinaryLogCommand = new DumpBinaryLogCommand(serverId, binlogFilename, binlogPosition);
            }
        }
        channel.write(dumpBinaryLogCommand);
    }

    private void requestBinaryLogStreamMaria(long serverId) throws IOException {
        Command dumpBinaryLogCommand;
        synchronized (gtidSetAccessLock) {
            if (gtidSet != null && gtidSet.toString().length() > 0) {
                String gtidStr = gtidSet.toString();
                // 拼接进 SQL 前校验格式，防止异常内容写入 @slave_connect_state
                if (!MARIA_GTID_SET_PATTERN.matcher(gtidStr).matches()) {
                    throw new IOException("Invalid MariaDB GTID set: " + gtidStr);
                }
                channel.write(new QueryCommand("SET @mariadb_slave_capability=4"));
                checkErrorPacket(channel.read());
                logger.info("MariaDB slave_connect_state: {}", gtidStr);
                channel.write(new QueryCommand("SET @slave_connect_state = '" + gtidStr + "'"));
                checkErrorPacket(channel.read());
                channel.write(new QueryCommand("SET @slave_gtid_strict_mode = 0"));
                checkErrorPacket(channel.read());
                channel.write(new QueryCommand("SET @slave_gtid_ignore_duplicates = 0"));
                checkErrorPacket(channel.read());
                dumpBinaryLogCommand = new DumpBinaryLogCommand(serverId, "", 0L, false);
            } else {
                dumpBinaryLogCommand = new DumpBinaryLogCommand(serverId, binlogFilename, binlogPosition);
            }
        }
        channel.write(dumpBinaryLogCommand);
    }

    private void ensureEventDataDeserializerIfPresent(EventType eventType,
                                                      Class<? extends EventDataDeserializer<?>> eventDataDeserializerClass) {
        EventDataDeserializer<?> eventDataDeserializer = eventDeserializer.getEventDataDeserializer(eventType);
        if (eventDataDeserializer.getClass() != eventDataDeserializerClass &&
                eventDataDeserializer.getClass() != EventDeserializer.EventDataWrapper.Deserializer.class) {
            EventDataDeserializer<?> internalEventDataDeserializer;
            try {
                internalEventDataDeserializer = eventDataDeserializerClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            eventDeserializer.setEventDataDeserializer(eventType,
                    new EventDeserializer.EventDataWrapper.Deserializer(internalEventDataDeserializer,
                            eventDataDeserializer));
        }
    }

    private Long getVersion() throws IOException {
        channel.write(new QueryCommand("SELECT VERSION()"));
        ResultSetRowPacket[] resultSet = readResultSet();
        if (resultSet.length == 0) {
            throw new IOException("Failed to getVersion, command SELECT VERSION()");
        }
        return parseVersion(resultSet[0].getValue(0));
    }

    /**
     * 将数据库版本字符串解析为可比较的数字编码：先截断 -/+ 或空格之后的后缀，
     * 去掉点后右补 0 至 10 位。示例：
     * "8.4.0" -> 8400000000，"5.7.44-log" -> 5744000000，
     * "10.11.6-MariaDB-1:10.11.6+maria~ubu2204" -> 1011600000。
     * 包私有且静态，便于单元测试。
     */
    static long parseVersion(String version) throws IOException {
        if (StringUtil.isBlank(version)) {
            throw new IOException("Empty database version");
        }
        String core = version.trim();
        // 截断后缀：5.7.44-log / 8.0.32+dfsg / 10.11.6-MariaDB-1:10.11.6+maria~ubu2204
        for (String separator : new String[]{StringUtil.HORIZONTAL, "+", StringUtil.SPACE}) {
            int idx = core.indexOf(separator);
            if (idx >= 0) {
                core = core.substring(0, idx);
            }
        }
        StringBuilder digits = new StringBuilder();
        for (String segment : StringUtil.split(core, StringUtil.POINT)) {
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                }
            }
        }
        if (digits.length() == 0) {
            throw new IOException("Unrecognized database version: " + version);
        }
        while (digits.length() < 10) {
            digits.append('0');
        }
        if (digits.length() > 10) {
            digits.setLength(10);
        }
        return Long.parseLong(digits.toString());
    }

    private void fetchBinlogFilenameAndPosition() throws IOException {
        if (getVersion() >= MYSQL_VERSION_8_4) {
            channel.write(new QueryCommand("SHOW BINARY LOG STATUS"));
        } else {
            channel.write(new QueryCommand("show master status"));
        }

        ResultSetRowPacket[] resultSet = readResultSet();
        if (resultSet.length == 0) {
            throw new IOException("Failed to determine binlog filename/position");
        }
        ResultSetRowPacket resultSetRow = resultSet[0];
        binlogFilename = resultSetRow.getValue(0);
        binlogPosition = Long.parseLong(resultSetRow.getValue(1));
    }

    private ChecksumType fetchBinlogChecksum() throws IOException {
        channel.write(new QueryCommand("show global variables like 'binlog_checksum'"));
        ResultSetRowPacket[] resultSet = readResultSet();
        if (resultSet.length == 0) {
            return ChecksumType.NONE;
        }
        String checksum = resultSet[0].getValue(1);
        for (ChecksumType checksumType : ChecksumType.values()) {
            if (StringUtil.equalsIgnoreCase(checksumType.name(), checksum)) {
                return checksumType;
            }
        }
        throw new IOException("Unsupported binlog_checksum value: " + checksum);
    }

    private void confirmSupportOfChecksum(final PacketChannel channel, ChecksumType checksumType) throws IOException {
        channel.write(new QueryCommand("set @master_binlog_checksum= @@global.binlog_checksum"));
        checkErrorPacket(channel.read());
        eventDeserializer.setChecksumType(checksumType);
    }

    private byte[] readPacketSplitInChunks(ByteArrayInputStream inputStream, int packetLength) throws IOException {
        byte[] result = inputStream.read(packetLength);
        int chunkLength;
        do {
            chunkLength = inputStream.readInteger(3);
            //noinspection ResultOfMethodCallIgnored
            inputStream.skip(1); // 1 byte for sequence
            result = Arrays.copyOf(result, result.length + chunkLength);
            inputStream.fill(result, result.length - chunkLength, chunkLength);
        } while (chunkLength == Packet.MAX_LENGTH);
        return result;
    }

    private void updateClientBinlogFilenameAndPosition(Event event) {
        EventHeader eventHeader = event.getHeader();
        EventType eventType = eventHeader.getEventType();
        if (eventType == EventType.ROTATE) {
            RotateEventData rotateEventData = (RotateEventData) EventDeserializer.EventDataWrapper.internal(event.getData());
            String newBinlogFilename = rotateEventData.getBinlogFilename();
            long newBinlogPosition = rotateEventData.getBinlogPosition();

            // 判断是否是真正的文件切换（而不是启动时的初始 ROTATE 事件）
            // 启动时的 ROTATE 事件特征：nextPosition=0 或 binlogFilename 没有变化
            boolean isRealRotation = false;
            if (eventHeader instanceof EventHeaderV4) {
                long nextPosition = ((EventHeaderV4) eventHeader).getNextPosition();
                // 如果 nextPosition > 0 且 binlogFilename 发生变化，说明是真正的文件切换
                if (nextPosition > 0 && !newBinlogFilename.equals(binlogFilename)) {
                    isRealRotation = true;
                }
            }

            binlogFilename = newBinlogFilename;
            binlogPosition = newBinlogPosition;

            // 只有在真正的文件切换时，才清理 TableMapEventData 缓存
            // 原因：
            // 1. 启动时的 ROTATE 事件（nextPosition=0）不应该清空缓存，因为后续的 ROWS 事件还需要表结构信息
            // 2. 每个 binlog 文件都有自己的 tableId 空间，当文件切换时，新文件中的 tableId 可能会被重新分配
            // 3. 如果不清理缓存，旧文件的 tableId 映射可能会被误用到新文件中，导致字段类型误判
            if (isRealRotation && tableMapEventByTableId != null) {
                tableMapEventByTableId.clear();
            }
        } else
            // do not update binlogPosition on TABLE_MAP so that in case of reconnect (using a different instance of
            // client) table mapping cache could be reconstructed before hitting row mutation event
            if (eventType != EventType.TABLE_MAP && eventHeader instanceof EventHeaderV4) {
                EventHeaderV4 trackableEventHeader = (EventHeaderV4) eventHeader;
                long nextBinlogPosition = trackableEventHeader.getNextPosition();
                if (nextBinlogPosition > 0) {
                    binlogPosition = nextBinlogPosition;
                }
            }
    }

    private void updateGtidSet(Event event) {
        synchronized (gtidSetAccessLock) {
            if (gtidSet == null) {
                return;
            }
        }
        EventHeader eventHeader = event.getHeader();
        switch (eventHeader.getEventType()) {
            case GTID:
                GtidEventData gtidEventData = (GtidEventData) EventDeserializer.EventDataWrapper.internal(event.getData());
                gtid = gtidEventData.getGtid();
                break;
            case MARIADB_GTID:
                MariadbGtidEventData mariadbGtidEventData = (MariadbGtidEventData) EventDeserializer.EventDataWrapper.internal(event.getData());
                gtid = mariadbGtidEventData.toString();
                break;
            case MARIADB_GTID_LIST:
                // MariaDB 服务器在连接初期下发的 GTID 清单，合并进当前集合，避免 GTID 模式重连时重复消费
                mergeMariadbGtidList((MariadbGtidListEventData) EventDeserializer.EventDataWrapper.internal(event.getData()));
                break;
            case XID:
                commitGtid();
                tx = false;
                break;
            case QUERY:
                QueryEventData queryEventData = (QueryEventData) EventDeserializer.EventDataWrapper.internal(event.getData());
                String sql = queryEventData.getSql();
                if (sql == null) {
                    break;
                }
                if ("BEGIN".equals(sql)) {
                    tx = true;
                } else if ("COMMIT".equals(sql) || "ROLLBACK".equals(sql)) {
                    commitGtid();
                    tx = false;
                } else if (!tx) {
                    // auto-commit query, likely DDL
                    commitGtid();
                }
                break;
            default:
                break;
        }
    }

    private void commitGtid() {
        String currentGtid = gtid;
        gtid = null;  // 提交后立即清空，避免重复提交
        if (currentGtid != null) {
            synchronized (gtidSetAccessLock) {
                gtidSet.add(currentGtid);
            }
        }
    }

    /**
     * 合并 MariaDB 服务器在连接初期下发的 GTID 清单（MARIADB_GTID_LIST 事件）。
     * 当前集合为空时直接采用；否则逐条并入（MariadbGtidSet.add 只接受单个 GTID）。
     */
    private void mergeMariadbGtidList(MariadbGtidListEventData gtidListEventData) {
        MariadbGtidSet receivedSet = gtidListEventData.getMariaGTIDSet();
        if (receivedSet == null) {
            return;
        }
        synchronized (gtidSetAccessLock) {
            if (gtidSet == null || gtidSet.toString().length() == 0) {
                gtidSet = receivedSet;
                return;
            }
            if (gtidSet instanceof MariadbGtidSet) {
                for (String singleGtid : StringUtil.split(receivedSet.toString(), StringUtil.COMMA)) {
                    String trimmed = singleGtid.trim();
                    if (trimmed.length() > 0) {
                        gtidSet.add(trimmed);
                    }
                }
            }
        }
    }

    private ResultSetRowPacket[] readResultSet() throws IOException {
        List<ResultSetRowPacket> resultSet = new ArrayList<>();
        byte[] statementResult = readPacketChecked(channel);
        checkErrorPacket(statementResult);
        // 跳过列定义包，直到遇到 EOF 包；期间收到错误包立即上抛
        for (byte[] packet; (packet = readPacketChecked(channel))[0] != (byte) 0xFE /* eof */; ) {
            checkErrorPacket(packet);
        }
        for (byte[] bytes; (bytes = readPacketChecked(channel))[0] != (byte) 0xFE /* eof */; ) {
            checkErrorPacket(bytes);
            resultSet.add(new ResultSetRowPacket(bytes));
        }
        return resultSet.toArray(new ResultSetRowPacket[0]);
    }

    private void closeChannel(final PacketChannel channel) throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    /**
     * 初始化事件反序列化器与表结构缓存。
     * 注意：eventDeserializer 仅在首次连接时构建（重连复用已有实例），
     * 因此反序列化器注册以首次连接为准。
     */
    private void initEventDeserializer() {
        if (null == tableMapEventByTableId) {
            // 该 map 会被 worker 线程（反序列化）与 keepAlive 线程（resetBinlogPosition 清理）并发访问
            tableMapEventByTableId = new ConcurrentHashMap<>();
        }

        if (null == eventDeserializer) {
            IdentityHashMap eventDataDeserializers = new IdentityHashMap();
            // Process event priority: RotateEvent > FormatDescriptionEvent > TableMapEvent > RowsEvent > XidEvent
            eventDataDeserializers.put(EventType.ROTATE, new RotateEventDataDeserializer());
            eventDataDeserializers.put(EventType.FORMAT_DESCRIPTION, new FormatDescriptionEventDataDeserializer());
            eventDataDeserializers.put(EventType.TABLE_MAP, new TableMapEventDataDeserializer());
            eventDataDeserializers.put(EventType.UPDATE_ROWS, new UpdateDeserializer(tableMapEventByTableId));
            eventDataDeserializers.put(EventType.WRITE_ROWS, new WriteDeserializer(tableMapEventByTableId));
            eventDataDeserializers.put(EventType.DELETE_ROWS, new DeleteDeserializer(tableMapEventByTableId));
            eventDataDeserializers.put(EventType.EXT_WRITE_ROWS, (new ExtWriteDeserializer(tableMapEventByTableId)).setMayContainExtraInformation(true));
            eventDataDeserializers.put(EventType.EXT_UPDATE_ROWS, (new ExtUpdateDeserializer(tableMapEventByTableId)).setMayContainExtraInformation(true));
            eventDataDeserializers.put(EventType.EXT_DELETE_ROWS, (new ExtDeleteDeserializer(tableMapEventByTableId)).setMayContainExtraInformation(true));
            eventDataDeserializers.put(EventType.XID, new XidEventDataDeserializer());
            eventDataDeserializers.put(EventType.INTVAR, new IntVarEventDataDeserializer());
            eventDataDeserializers.put(EventType.QUERY, new QueryEventDataDeserializer());
            eventDataDeserializers.put(EventType.ROWS_QUERY, new RowsQueryEventDataDeserializer());
            eventDataDeserializers.put(EventType.GTID, new GtidEventDataDeserializer());
            eventDataDeserializers.put(EventType.PREVIOUS_GTIDS, new PreviousGtidSetDeserializer());
            eventDataDeserializers.put(EventType.XA_PREPARE, new XAPrepareEventDataDeserializer());
            this.eventDeserializer = new EventDeserializer(new EventHeaderV4Deserializer(), new NullEventDataDeserializer(), eventDataDeserializers, tableMapEventByTableId);
        }
    }

    private void notifyEventListeners(Event event) throws Exception {
        if (event.getData() instanceof EventDeserializer.EventDataWrapper) {
            event = new Event(event.getHeader(), ((EventDeserializer.EventDataWrapper) event.getData()).getExternal());
        }
        for (BinaryLogRemoteClient.EventListener eventListener : eventListeners) {
            try {
                eventListener.onEvent(event);
            } catch (Exception e) {
                // 不再吞掉监听器异常：事件丢失意味着数据不一致，
                // 重抛后由事件循环走 notifyException -> 重连/告警路径
                logger.warn(eventListener + " choked on " + event, e);
                throw e;
            }
        }
    }

    /**
     * 确定 serverId：未指定（0）时取随机 UUID 低 32 位（约 42.9 亿种可能，
     * 碰撞概率远低于随机端口），结果为 0 时返回 1（0 有特殊含义）。
     */
    private long resolveServerId(long serverId) {
        if (0 == serverId) {
            long uuidBasedId = java.util.UUID.randomUUID().getLeastSignificantBits() & 0xFFFFFFFFL;
            return uuidBasedId == 0 ? 1 : uuidBasedId;
        }
        return serverId;
    }

    private void spawnWorkerThread() {
        // 固定捕获当前 channel 引用：重连后 this.channel 会指向新实例，旧 worker 不得读取新连接
        final PacketChannel currentChannel = this.channel;
        String name = "binlog-parser-" + createClientId();
        this.worker = new Thread(() -> listenForEventPackets(currentChannel));
        this.worker.setDaemon(false);
        this.worker.setName(name);
        this.worker.start();
    }

    private void spawnKeepAliveThread() {
        String clientId = createClientId();
        this.keepAlive = new Thread(() -> {
            while (connected) {
                if (connectedError) {
                    break;
                }
                try {
                    TimeUnit.SECONDS.sleep(15);
                    channel.write(new PingCommand());
                } catch (Exception e) {
                    notifyException(e);
                    break;
                }
            }
            // 使用 RetryInterceptor 进行指数退避重连
            RetryPolicy policy = new RetryPolicy();
            policy.setInitialIntervalMs(KEEPALIVE_RETRY_INITIAL_MS);
            policy.setMaxIntervalMs(KEEPALIVE_RETRY_MAX_MS);
            policy.setMultiplier(2.0);
            policy.setTerminationMode(TerminationMode.MAX_DURATION);
            policy.setMaxDurationMs(0);  // 无限重试
            policy.setUseKeyword(false);

            RetryInterceptor retry = new RetryInterceptor();
            try {
                retry.execute(
                    () -> {
                        if (!connected) {
                            logger.warn("Trying to stop");
                            return null;  // 用户已关闭，停止重试
                        }
                        logger.info("Trying to restore lost connection to {}", clientId);
                        disconnect();
                        connect();
                        return null;
                    },
                    policy,
                    clientId,
                    RetryAfter.slf4j(logger)
                );
                connectedError = false;
            } catch (Exception e) {
                // 重试被中断或其他异常
                logger.error("Reconnect failed: {}", e.getMessage(), e);
                notifyException(e);
            }
        });
        this.keepAlive.setDaemon(false);
        this.keepAlive.setName("binlog-keepalive-" + clientId);
        this.keepAlive.start();
    }

    private void notifyException(Exception e) {
        if (connected) {
            if (e instanceof ServerException && isBogusDataError((ServerException) e)) {
                logger.warn("检测到 MySQL 重启导致 binlog 位置无效 (bogus data, 错误码 1236)，将重置位置重新同步");
                resetBinlogPosition();
            }
            logger.error(e.getMessage(), e);
            lifecycleListeners.forEach(listener -> listener.onException(this, e));
            connectedError = true;
        }
    }

    private boolean isBogusDataError(ServerException e) {
        return e.getErrorCode() == 1236
                && e.getMessage() != null
                && e.getMessage().contains("bogus data");
    }

    private void resetBinlogPosition() {
        boolean gtidMode;
        synchronized (gtidSetAccessLock) {
            gtidMode = gtidSet != null && gtidSet.toString().length() > 0;
        }
        if (!gtidMode) {
            logger.warn("非 GTID 模式重置 binlog 位置：重连将跳转到最新位点 (binlogFilename={}, binlogPosition={})，故障点至今的事件可能丢失",
                    binlogFilename, binlogPosition);
        }
        this.binlogFilename = null;
        this.binlogPosition = 4;
        if (tableMapEventByTableId != null) {
            tableMapEventByTableId.clear();
        }
    }

    @Override
    public String getBinlogFilename() {
        return binlogFilename;
    }

    @Override
    public void setBinlogFilename(String binlogFilename) {
        this.binlogFilename = binlogFilename;
    }

    @Override
    public long getBinlogPosition() {
        return binlogPosition;
    }

    @Override
    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    @Override
    public EventDeserializer getEventDeserializer() {
        return eventDeserializer;
    }

    @Override
    public void setEventDeserializer(EventDeserializer eventDeserializer) {
        if (eventDeserializer == null) {
            throw new IllegalArgumentException("Event deserializer cannot be NULL");
        }
        this.eventDeserializer = eventDeserializer;
    }

    @Override
    public Map<Long, TableMapEventData> getTableMapEventByTableId() {
        return tableMapEventByTableId;
    }

    @Override
    public void setTableMapEventByTableId(Map<Long, TableMapEventData> tableMapEventByTableId) {
        this.tableMapEventByTableId = tableMapEventByTableId;
    }

    @Override
    public String getWorkerThreadName() {
        Thread w = this.worker;
        return w != null ? w.getName() : null;
    }

    public SSLMode getSSLMode() {
        return sslMode;
    }

    public void setSSLMode(SSLMode sslMode) {
        if (sslMode == null) {
            throw new IllegalArgumentException("SSL mode cannot be NULL");
        }
        this.sslMode = sslMode;
    }

    /**
     * @return GTID set. Note that this value changes with each received GTID event (provided client is in GTID mode).
     * @see #setGtidSet(String)
     */
    public String getGtidSet() {
        synchronized (gtidSetAccessLock) {
            return gtidSet != null ? gtidSet.toString() : null;
        }
    }

    /**
     * @param gtidStr GTID set string (can be an empty string).
     *                <p>NOTE #1: Any value but null will switch BinaryLogRemoteClient into a GTID mode (this will also set binlogFilename
     *                to "" (provided it's null) forcing MySQL to send events starting from the oldest known binlog).
     *                <p>NOTE #2: GTID set is automatically updated with each incoming GTID event (provided GTID mode is on).
     * @see #getGtidSet()
     */
    public void setGtidSet(String gtidStr) {
        if (gtidStr == null)
            return;

        synchronized (gtidSetAccessLock) {
            if (MariadbGtidSet.isMariaGtidSet(gtidStr)) {
                this.gtidSet = new MariadbGtidSet(gtidStr);
            } else {
                this.gtidSet = new GtidSet(gtidStr);
            }
        }
    }

    public interface EventListener {

        void onEvent(Event event);
    }

    public interface LifecycleListener {

        /**
         * Called once client has successfully logged in but before started to receive binlog events.
         */
        void onConnect(BinaryLogRemoteClient client);

        /**
         * It's guarantied to be called before {@link #onDisconnect(BinaryLogRemoteClient)}) in case of communication failure.
         */
        void onException(BinaryLogRemoteClient client, Exception ex);

        /**
         * Called upon disconnect (regardless of the reason).
         */
        void onDisconnect(BinaryLogRemoteClient client);
    }

}