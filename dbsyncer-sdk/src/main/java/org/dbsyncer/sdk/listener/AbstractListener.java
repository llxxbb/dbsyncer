/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.listener;

import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-25 22:35
 */
public abstract class AbstractListener<C extends ConnectorInstance> implements Listener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected static LogService staticLogService;
    protected static MessageService staticMessageService;
    protected C connectorInstance;
    protected ConnectorInstance targetConnectorInstance;
    protected ConnectorService connectorService;
    protected ScheduledTaskService scheduledTaskService;
    protected ConnectorConfig connectorConfig;
    protected ListenerConfig listenerConfig;
    protected Set<String> filterTable;
    protected List<Table> sourceTable;
    protected Map<String, String> snapshot;
    protected String metaId;
    protected Mapping mapping;
    private Watcher watcher;
    // 保存上次持久化的 snapshot，用于判断是否发生变化
    private Map<String, String> lastFlushedSnapshot;
    // 待持久化的快照点（异步批量持久化）
    private volatile Map<String, String> pendingSnapshot = null;
    // 异步持久化执行器
    private ScheduledExecutorService flushExecutor;
    // 关闭标志：置位后不再接受快照推进，也不再持久化快照（ADR-0036 决策 5）
    private volatile boolean closed = false;

    @Override
    public void register(Watcher watcher) {
        this.watcher = watcher;
    }

    @Override
    public void changeEventBefore(QuartzListenerContext context) {
        watcher.changeEventBefore(context);
    }

    @Override
    public void changeEvent(ChangedEvent event) {
        if (null != event) {
            switch (event.getEvent()) {
                case ConnectorConstant.OPERTION_UPDATE:
                    // 是否支持监听修改事件
                    processEvent(listenerConfig.isEnableUpdate(), event);
                    break;
                case ConnectorConstant.OPERTION_INSERT:
                    // 是否支持监听新增事件
                    processEvent(listenerConfig.isEnableInsert(), event);
                    break;
                case ConnectorConstant.OPERTION_DELETE:
                    // 是否支持监听删除事件
                    processEvent(listenerConfig.isEnableDelete(), event);
                    break;
                case ConnectorConstant.OPERTION_ALTER:
                    // 表结构变更事件：检查 DDL 开关
                    processEvent(listenerConfig.isEnableDDL(), event);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void refreshEvent(ChangedOffset offset) {
        // 监听器已关闭：断开窗口内到达的事件不得推进快照点，否则续读点会越过未落库的数据
        if (closed) {
            logger.warn("监听器已关闭，忽略快照推进: metaId={}", metaId);
            return;
        }
        pendingSnapshot = new HashMap<>(snapshot);
    }

    @Override
    public void init() {
        // 初始化异步持久化执行器
        initFlushExecutor();
    }

    /**
     * 初始化异步持久化执行器
     * 在 Listener 初始化时调用（通过 init() 方法）
     */
    private void initFlushExecutor() {
        if (flushExecutor == null) {
            flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "snapshot-flush-" + metaId);
                t.setDaemon(true);
                logger.info("snapshot flusher created for: metaId {},", metaId);
                return t;
            });
            // 每 3 秒检查并写入最新快照
            flushExecutor.scheduleWithFixedDelay(() -> {
                if (closed) {
                    return;
                }
                flushPendingSnapshot();
            }, 3, 3, TimeUnit.SECONDS);
        }
    }

    /**
     * 持久化待写入的快照点。与上次持久化的内容相同则跳过，避免重复写入。
     */
    private void flushPendingSnapshot() {
        Map<String, String> current = pendingSnapshot;
        if (watcher == null || current == null || Objects.equals(current, lastFlushedSnapshot)) {
            return;
        }
        try {
            logger.info("snapshot changed, flushing: {}", current);
            watcher.flushEvent(current);
            lastFlushedSnapshot = new HashMap<>(current);
        } catch (Exception e) {
            logger.error("快照点持久化失败", e);
        }
    }

    private void shutdownFlushExecutor() {
        if (flushExecutor == null) {
            return;
        }
        flushExecutor.shutdown();
        try {
            // 等待正在执行的任务完成
            if (!flushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                flushExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取是否有任务数据在处理（通过 Watcher 接口）
     *
     * @return true 表示有任务数据在处理，false 表示没有
     */
    public boolean hasPendingTaskData() {
        return watcher != null && watcher.hasPendingTask();
    }

    /**
     * 系统关闭时停止持久化线程
     */
    @PreDestroy
    public void destroy() {
        closed = true;
        shutdownFlushExecutor();
    }

    @Override
    public void errorEvent(Exception e) {
        watcher.errorEvent(e);
    }

    protected void sleepInMills(long timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * 如果允许监听该事件，则触发事件通知
     *
     * @param permitEvent
     * @param event
     */
    private void processEvent(boolean permitEvent, ChangedEvent event) {
        if (permitEvent) {
            watcher.changeEvent(event);
        }
    }

    @SuppressWarnings("unchecked")
    public void setConnectorInstance(ConnectorInstance connectorInstance) {
        this.connectorInstance = (C) connectorInstance;
    }

    public C getConnectorInstance() {
        return connectorInstance;
    }

    public void setTargetConnectorInstance(ConnectorInstance targetConnectorInstance) {
        this.targetConnectorInstance = targetConnectorInstance;
    }

    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    public void setScheduledTaskService(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    public void setConnectorConfig(ConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }

    public void setListenerConfig(ListenerConfig listenerConfig) {
        this.listenerConfig = listenerConfig;
    }

    public void setFilterTable(Set<String> filterTable) {
        this.filterTable = filterTable;
    }

    public AbstractListener setSourceTable(List<Table> sourceTable) {
        this.sourceTable = sourceTable;
        return this;
    }

    public void setSnapshot(Map<String, String> snapshot) {
        // 创建副本，避免直接修改 Meta 对象的 snapshot（Meta 对象被缓存，直接修改会影响持久化存储）
        this.snapshot = snapshot != null ? new HashMap<>(snapshot) : new HashMap<>();
        // 初始化时，如果 snapshot 不为空，也初始化 lastFlushedSnapshot
        if (this.lastFlushedSnapshot == null && !CollectionUtils.isEmpty(snapshot)) {
            this.lastFlushedSnapshot = new HashMap<>(snapshot);
        }
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

    public static void setStaticLogService(LogService logService) {
        staticLogService = logService;
    }

    public static LogService getStaticLogService() {
        return staticLogService;
    }

    public static void setStaticMessageService(MessageService messageService) {
        staticMessageService = messageService;
    }

    public static MessageService getStaticMessageService() {
        return staticMessageService;
    }

    public void setMapping(Mapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public void close() {
        closed = true;
        shutdownFlushExecutor();
        staticLogService.log(mapping, LogType.STOP, "结束增量同步");
    }

}