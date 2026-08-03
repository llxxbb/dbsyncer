/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.base;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dbsyncer.common.config.RetryConfig;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.retry.RetryInterceptor;
import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.sdk.config.CommandConfig;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.AbstractConnector;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 连接器工厂
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2019-09-18 23:30
 */
@Component
public class ConnectorFactory implements DisposableBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, ConnectorInstance> pool = new ConcurrentHashMap<>();

    private final Map<String, ConnectorService> service = new ConcurrentHashMap<>();

    private final Set<String> connectorTypes = new HashSet<>();

    @Resource
    private LogService logService;

    @Resource
    private RetryConfig retryConfig;

    private static final RetryInterceptor retryInterceptor = new RetryInterceptor();

    @PostConstruct
    private void init() {
        ServiceLoader<ConnectorService> services = ServiceLoader.load(ConnectorService.class,
                Thread.currentThread().getContextClassLoader());
        for (ConnectorService s : services) {
            service.putIfAbsent(s.getConnectorType(), s);
            connectorTypes.add(s.getConnectorType());
        }
    }

    @Override
    public void destroy() {
        pool.values().forEach(this::disconnect);
        pool.clear();
    }

    /**
     * 建立连接，返回缓存连接对象（默认验证连接）
     *
     * @param config
     */
    public ConnectorInstance connect(ConnectorConfig config) throws Exception {
        return connect(config, true);
    }

    /**
     * 建立连接，返回缓存连接对象
     *
     * @param config     连接配置
     * @param checkAlive 是否检查连接是否存活。false 时跳过连接验证，用于初始化场景避免触发连接检查（如 kafka 连接异常）
     */
    public ConnectorInstance connect(ConnectorConfig config, boolean checkAlive) throws Exception {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        ConnectorService connectorService = getConnectorService(config);
        String cacheKey = connectorService.getConnectorInstanceCacheKey(config);
        if (!pool.containsKey(cacheKey)) {
            synchronized (pool) {
                if (!pool.containsKey(cacheKey)) {
                    ConnectorInstance instance = connectorService.connect(config);
                    // 只有在需要检查时才调用 isAlive，避免在初始化时触发连接检查
                    if (checkAlive) {
                        Assert.isTrue(connectorService.isAlive(instance), "连接配置异常");
                    }
                    pool.putIfAbsent(cacheKey, instance);
                }
            }
        }
        try {
            ConnectorInstance connectorInstance = pool.get(cacheKey);
            ConnectorInstance clone = (ConnectorInstance) connectorInstance.clone();
            clone.setConfig(config);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new ConnectorException(e);
        }
    }

    /**
     * 获取监听器
     *
     * @param connectorType
     * @param listenerType
     * @return
     */
    public Listener getListener(String connectorType, String listenerType) {
        return getConnectorService(connectorType).getListener(listenerType);
    }

    /**
     * 检查连接配置是否可用
     *
     * @param config
     * @return
     */
    public boolean isAlive(ConnectorConfig config) throws Exception {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        ConnectorService connectorService = getConnectorService(config);
        String cacheKey = connectorService.getConnectorInstanceCacheKey(config);
        if (pool.containsKey(cacheKey)) {
            return connectorService.isAlive(pool.get(cacheKey));
        }
        return false;
    }

    /**
     * 获取配置表
     *
     * @param connectorInstance
     * @return
     */
    public List<Table> getTable(ConnectorInstance connectorInstance) throws Exception {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        List tableList = getConnectorService(connectorInstance.getConfig()).getTable(connectorInstance);
        // 按升序展示表
        Collections.sort(tableList, Comparator.comparing(Table::getName));
        return tableList;
    }

    /**
     * 获取配置表元信息
     *
     * @param connectorInstance
     * @param tableName
     * @return
     */
    public MetaInfo getMetaInfo(ConnectorInstance connectorInstance, String tableName) throws Exception {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        Assert.hasText(tableName, "tableName can not be empty.");
        List<Table> table = getTable(connectorInstance);
        MetaInfo metaInfo = getConnectorService(connectorInstance.getConfig()).getMetaInfo(connectorInstance,
                tableName);
        if (metaInfo == null) {
            return null;
        }
        if (!CollectionUtils.isEmpty(table)) {
            for (Table t : table) {
                if (t.getName().equals(tableName)) {
                    metaInfo.setType(t.getType());
                    metaInfo.setSql(t.getSql());
                    break;
                }
            }
        }
        return metaInfo;
    }

    public Object getPosition(ConnectorInstance connectorInstance) throws Exception {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        return getConnectorService(connectorInstance.getConfig()).getPosition(connectorInstance);
    }

    /**
     * 获取连接器同步参数
     *
     * @param sourceCommandConfig
     * @param targetCommandConfig
     * @return
     */
    public Map<String, String> getCommand(CommandConfig sourceCommandConfig, CommandConfig targetCommandConfig)
            throws Exception {
        Assert.notNull(sourceCommandConfig, "SourceCommandConfig can not be null.");
        Assert.notNull(targetCommandConfig, "TargetCommandConfig can not be null.");
        Map<String, String> map = new HashMap<>();
        Map sCmd = getConnectorService(sourceCommandConfig.getConnectorType()).getSourceCommand(sourceCommandConfig);
        if (!CollectionUtils.isEmpty(sCmd)) {
            map.putAll(sCmd);
        }

        Map tCmd = getConnectorService(targetCommandConfig.getConnectorType()).getTargetCommand(targetCommandConfig);
        if (!CollectionUtils.isEmpty(tCmd)) {
            map.putAll(tCmd);
        }
        return map;
    }

    public long getCount(ConnectorInstance connectorInstance, Map<String, String> command) throws Exception {
        Assert.notNull(connectorInstance, "ConnectorInstance can not null");
        Assert.notNull(command, "command can not null");
        return getConnectorService(connectorInstance.getConfig()).getCount(connectorInstance, command);
    }

    /**
     * 批量写入数据到目标源
     * @throws Exception 
     */
    public Result writeBatch(List<SyncRow> syncRows) throws Exception {
        if (CollectionUtils.isEmpty(syncRows)) {
            return new Result();
        }
        PluginContext ctx = syncRows.get(0).getContext();
        String event = ctx.getEvent();
        boolean forceUpdate = syncRows.get(0).getMapping().isForceUpdate();
        if (forceUpdate && ConnectorConstant.isInsert(event)) {
            event = ConnectorConstant.OPERTION_UPSERT;
            ctx.setEvent(event);
        }
        RetryPolicy policy = retryConfig != null ? retryConfig.getGlobal() : new RetryPolicy();
        String mappingId = syncRows.get(0).getMapping() != null ? syncRows.get(0).getMapping().getId() : null;
        switch (event) {
            case ConnectorConstant.OPERTION_INSERT:
                return executeWriterOperation(syncRows, (connector, instance, rows) -> connector.insert(instance, rows),
                        policy, mappingId);
            case ConnectorConstant.OPERTION_UPDATE:
                return executeWriterOperation(syncRows, (connector, instance, rows) -> connector.update(instance, rows),
                        policy, mappingId);
            case ConnectorConstant.OPERTION_DELETE:
                return executeWriterOperation(syncRows, (connector, instance, rows) -> connector.delete(instance, rows),
                        policy, mappingId);
            case ConnectorConstant.OPERTION_UPSERT:
                return executeWriterOperation(syncRows, (connector, instance, rows) -> connector.upsert(instance, rows),
                        policy, mappingId);
            default:
                throw new RuntimeException("不支持的事件类型：" + event);
        }
    }

    private Result executeWriterOperation(List<SyncRow> syncRows, WriterOperation operation, RetryPolicy policy,
            String mappingId) throws Exception {
        PluginContext ctx = syncRows.get(0).getContext();
        ConnectorInstance targetInstance = ctx.getTargetConnectorInstance();
        Assert.notNull(targetInstance, "targetConnectorInstance can not null");
        ConnectorService targetConnector = getConnectorService(targetInstance.getConfig());

        SyncRow firstRow = syncRows.get(0);
        String tableName = firstRow.getTableGroup() != null
                ? firstRow.getTableGroup().getSourceTable().getName()
                : "";

        if (targetConnector instanceof AbstractConnector) {
            AbstractConnector conn = (AbstractConnector) targetConnector;
            if (ctx.isEnableSchemaResolver() && targetConnector.getSchemaResolver() != null) {
                try {
                    conn.convertProcessBeforeWriter(syncRows, targetConnector.getSchemaResolver());
                } catch (Exception e) {
                    logger.error("traceId:{}, tableName:{}, event:{}", ctx.getTraceId(), tableName,
                            ctx.getEvent(), e);
                    logService.log(firstRow.getMapping(), LogType.SYNC, LogLevel.ERROR,
                            e.getMessage() + ExceptionUtils.getStackTrace(e));
                    throw e;
                }
            }
        }

        return retryInterceptor.execute(() -> {
            Result result = operation.execute(targetConnector, targetInstance, syncRows);
            Assert.notNull(result, "Connector writer batch result can not null");
            return result;
        }, policy, mappingId);
    }

    /**
     * 写入操作接口
     */
    @FunctionalInterface
    private interface WriterOperation {
        Result execute(ConnectorService connector, ConnectorInstance instance, List<SyncRow> syncRows);
    }

    public Result writerDDL(ConnectorInstance connectorInstance, DDLConfig ddlConfig,
            org.dbsyncer.sdk.plugin.PluginContext context) throws Exception {
        Assert.notNull(connectorInstance, "ConnectorInstance can not null");
        Result result = getConnectorService(connectorInstance.getConfig()).writerDDL(connectorInstance, ddlConfig,
                context);
        Assert.notNull(result, "Connector writer batch result can not null");
        return result;
    }

    public ConnectorService getConnectorService(ConnectorConfig connectorConfig) {
        Assert.notNull(connectorConfig, "ConnectorConfig can not null");
        return getConnectorService(connectorConfig.getConnectorType());
    }

    public ConnectorService getConnectorService(String connectorType) {
        ConnectorService connectorService = service.get(connectorType);
        if (connectorService == null) {
            Assert.isTrue(false, "Unsupported connector type:" + connectorType);
        }
        return connectorService;
    }

    public Set<String> getConnectorTypeAll() {
        return connectorTypes;
    }

    /**
     * 断开连接
     *
     * @param config
     * @return
     */
    public void disconnect(ConnectorConfig config) {
        Assert.notNull(config, "ConnectorConfig can not be null.");
        String cacheKey = getConnectorService(config).getConnectorInstanceCacheKey(config);
        ConnectorInstance connectorInstance = pool.get(cacheKey);
        if (connectorInstance != null) {
            disconnect(connectorInstance);
            pool.remove(cacheKey);
        }
    }

    private void disconnect(ConnectorInstance connectorInstance) {
        Assert.notNull(connectorInstance, "ConnectorInstance can not be null.");
        getConnectorService(connectorInstance.getConfig()).disconnect(connectorInstance);
    }

}