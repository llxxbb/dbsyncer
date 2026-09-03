package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.sdk.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * ADR-0029/0030：SystemConfigRegistry - 三源分离架构 + 配置变更通知。
 * <p>
 * 持有三个 SystemConfig 实例：
 * <ul>
 *   <li>propsConfig - 从 Spring Environment 加载（A/B/C 类配置）</li>
 *   <li>dbConfig - 从数据库加载（B/C 类配置）</li>
 *   <li>mergedConfig - 合并后的最终配置（DB 优先）</li>
 * </ul>
 * <p>
 * 业务代码只通过 {@link #getConfig()} 获取最终配置。
 * <p>
 * 配置变更时，通过 {@link ConfigChangeListener} 通知所有监听的 Bean，
 * 各 Bean 自行决定如何更新内部状态和清理缓存。
 */
@Component
public class SystemConfigRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigRegistry.class);

    @Resource
    private Environment environment;

    @Resource
    private StorageService storageService;

    // 三个实例
    private SystemConfig propsConfig;   // 从 Environment 加载
    private SystemConfig dbConfig;      // 从数据库加载
    private SystemConfig mergedConfig;  // 合并后的（最终使用）

    /**
     * 启动时初始化。
     */
    @PostConstruct
    public void init() {
        this.propsConfig = loadFromEnvironment();
        this.dbConfig = loadFromDatabase();
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 初始化完成");
    }

    /**
     * 业务代码只调用这个方法，获取合并后的最终配置。
     */
    public SystemConfig getConfig() {
        return mergedConfig;
    }

    /**
     * 获取 props 配置（用于对照表打印）。
     */
    public SystemConfig getPropsConfig() {
        return propsConfig;
    }

    /**
     * 获取 DB 配置（用于对照表打印）。
     */
    public SystemConfig getDbConfig() {
        return dbConfig;
    }

    /**
     * 设置 DB 配置（内部过滤 A 类，因为 A 类只从 props 来，DB 不存储）。
     * <p>
     * 由 ConfigMigrationService 调用，用于首次迁移时把 props 默认值写入 DB。
     */
    public synchronized void setDbConfig(SystemConfig newDbConfig) {
        this.dbConfig = filterAClass(newDbConfig);
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 设置 dbConfig 完成，已过滤 A 类配置");
    }

    /**
     * DB 配置变更后重新合并。
     * <p>
     * 由 SystemConfigServiceImpl.editTab() 调用。
     * 调用方需自行决定是否通知 ConfigChangeListener。
     */
    public synchronized void onDbConfigChanged(SystemConfig newDbConfig) {
        this.dbConfig = newDbConfig;
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 重新合并完成");
    }

    /**
     * 过滤 A 类配置（A 类只从 props 来，DB 不存储）。
     */
    private SystemConfig filterAClass(SystemConfig config) {
        SystemConfig filtered = new SystemConfig();
        // B/C 类：保留
        filtered.setExecutors(config.getExecutors());
        filtered.setBuffers(config.getBuffers());
        filtered.setRetry(config.getRetry());
        filtered.setNotify(config.getNotify());
        filtered.setError(config.getError());
        filtered.setBasic(config.getBasic());
        filtered.setSchemaVersion(config.getSchemaVersion());
        // A 类：不设置（server, storageConn, logging, helpdoc, worker, license）
        return filtered;
    }

    // ===== 配置加载 =====

    /**
     * 从 Environment 加载 A/B/C 类配置。
     */
    private SystemConfig loadFromEnvironment() {
        SystemConfig config = new SystemConfig();

        // ===== A 类 =====

        ServerProps server = new ServerProps();
        server.setPort(environment.getProperty("server.port", Integer.class));
        server.setSslEnabled(environment.getProperty("server.ssl.enabled", Boolean.class));
        server.setHttpPort(environment.getProperty("server.http.port", Integer.class));
        server.setHttpRedirect(environment.getProperty("server.http.redirect", Boolean.class));
        server.setContextPath(environment.getProperty("server.servlet.context-path", String.class));
        server.setSessionTimeout(environment.getProperty("server.servlet.session.timeout", Integer.class));
        server.setSslKeyStore(environment.getProperty("server.ssl.key-store", String.class));
        server.setSslKeyStorePassword(environment.getProperty("server.ssl.key-store-password", String.class));
        server.setSslKeyStoreType(environment.getProperty("server.ssl.key-store-type", String.class));
        config.setServer(server);

        StorageConnectionProps storageConn = new StorageConnectionProps();
        storageConn.setType(environment.getProperty("dbsyncer.storage.type", String.class));
        storageConn.setMysqlUrl(environment.getProperty("dbsyncer.storage.mysql.url", String.class));
        storageConn.setMysqlUsername(environment.getProperty("dbsyncer.storage.mysql.username", String.class));
        storageConn.setMysqlPassword(environment.getProperty("dbsyncer.storage.mysql.password", String.class));
        config.setStorageConn(storageConn);

        LoggingProps logging = new LoggingProps();
        logging.setLevelRoot(environment.getProperty("logging.level.root", String.class));
        config.setLogging(logging);

        HelpdocProps helpdoc = new HelpdocProps();
        helpdoc.setPath(environment.getProperty("helpdoc.path", String.class));
        config.setHelpdoc(helpdoc);

        WorkerProps worker = new WorkerProps();
        worker.setId(environment.getProperty("dbsyncer.web.worker.id", Long.class));
        worker.setDataCenterId(environment.getProperty("dbsyncer.web.worker.data-center-id", Long.class));
        config.setWorker(worker);

        LicenseProps license = new LicenseProps();
        license.setServerAddress(environment.getProperty("dbsyncer.web.license.server.address", String.class));
        config.setLicense(license);

        // ===== B 类（执行器 + 缓冲区） =====

        ExecutorProps executors = new ExecutorProps();
        PoolProps generalPool = new PoolProps();
        generalPool.setThreadCoreSize(environment.getProperty("dbsyncer.parser.general.thread-core-size", Integer.class));
        generalPool.setMaxThreadSize(environment.getProperty("dbsyncer.parser.general.max-thread-size", Integer.class));
        generalPool.setThreadQueueCapacity(environment.getProperty("dbsyncer.parser.general.thread-queue-capacity", Integer.class));
        executors.setGeneral(generalPool);

        PoolProps storagePool = new PoolProps();
        storagePool.setThreadCoreSize(environment.getProperty("dbsyncer.storage.thread-core-size", Integer.class));
        storagePool.setMaxThreadSize(environment.getProperty("dbsyncer.storage.max-thread-size", Integer.class));
        storagePool.setThreadQueueCapacity(environment.getProperty("dbsyncer.storage.thread-queue-capacity", Integer.class));
        executors.setStorage(storagePool);

        PoolProps dispatchPool = new PoolProps();
        dispatchPool.setThreadCoreSize(environment.getProperty("dbsyncer.common.dispatch.thread-core-size", Integer.class));
        dispatchPool.setMaxThreadSize(environment.getProperty("dbsyncer.common.dispatch.max-thread-size", Integer.class));
        dispatchPool.setThreadQueueCapacity(environment.getProperty("dbsyncer.common.dispatch.thread-queue-capacity", Integer.class));
        executors.setDispatch(dispatchPool);

        executors.setSchedulerPoolSize(environment.getProperty("dbsyncer.web.scheduler.pool-size", Integer.class));
        config.setExecutors(executors);

        BufferProps buffers = new BufferProps();
        BufferActuatorProps generalBuffer = new BufferActuatorProps();
        generalBuffer.setBufferWriterCount(environment.getProperty("dbsyncer.parser.general.buffer-writer-count", Integer.class));
        generalBuffer.setBufferPullCount(environment.getProperty("dbsyncer.parser.general.buffer-pull-count", Integer.class));
        generalBuffer.setBufferQueueCapacity(environment.getProperty("dbsyncer.parser.general.buffer-queue-capacity", Integer.class));
        generalBuffer.setBufferPeriodMillisecond(environment.getProperty("dbsyncer.parser.general.buffer-period-millisecond", Integer.class));
        buffers.setGeneral(generalBuffer);

        BufferActuatorProps storageBuffer = new BufferActuatorProps();
        storageBuffer.setBufferWriterCount(environment.getProperty("dbsyncer.storage.buffer-writer-count", Integer.class));
        storageBuffer.setBufferPullCount(environment.getProperty("dbsyncer.storage.buffer-pull-count", Integer.class));
        storageBuffer.setBufferQueueCapacity(environment.getProperty("dbsyncer.storage.buffer-queue-capacity", Integer.class));
        storageBuffer.setBufferPeriodMillisecond(environment.getProperty("dbsyncer.storage.buffer-period-millisecond", Integer.class));
        buffers.setStorage(storageBuffer);
        config.setBuffers(buffers);

        // ===== C 类（重试 + 通知 + 错误） =====

        RetryProps retry = new RetryProps();
        retry.setDisable(environment.getProperty("dbsyncer.retry.global.disable", Boolean.class));
        retry.setMaxAttempts(environment.getProperty("dbsyncer.retry.global.max-attempts", Integer.class));
        retry.setInitialIntervalMs(environment.getProperty("dbsyncer.retry.global.initial-interval-ms", Long.class));
        retry.setMaxIntervalMs(environment.getProperty("dbsyncer.retry.global.max-interval-ms", Long.class));
        retry.setMultiplier(environment.getProperty("dbsyncer.retry.global.multiplier", Double.class));
        retry.setMaxDurationMs(environment.getProperty("dbsyncer.retry.global.max-duration-ms", Long.class));
        retry.setUseKeyword(environment.getProperty("dbsyncer.retry.global.use-keyword", Boolean.class));
        config.setRetry(retry);

        NotifyProps notify = new NotifyProps();
        List<String> notifyChannels = environment.getProperty("dbsyncer.plugin.notify.channels", List.class);
        if (notifyChannels != null) {
            notify.setChannels(notifyChannels);
        }
        MailProps mail = new MailProps();
        mail.setUsername(environment.getProperty("dbsyncer.plugin.notify.mail.username", String.class));
        mail.setPassword(environment.getProperty("dbsyncer.plugin.notify.mail.password", String.class));
        List<String> mailNotifyTypes = environment.getProperty("dbsyncer.plugin.notify.mail.notify-types", List.class);
        if (mailNotifyTypes != null) {
            mail.setNotifyTypes(mailNotifyTypes);
        }
        notify.setMail(mail);
        DingTalkProps dingtalk = new DingTalkProps();
        dingtalk.setWebhookUrl(environment.getProperty("dbsyncer.plugin.notify.dingtalk.webhook-url", String.class));
        dingtalk.setSecret(environment.getProperty("dbsyncer.plugin.notify.dingtalk.secret", String.class));
        List<String> dingtalkNotifyTypes = environment.getProperty("dbsyncer.plugin.notify.dingtalk.notify-types", List.class);
        if (dingtalkNotifyTypes != null) {
            dingtalk.setNotifyTypes(dingtalkNotifyTypes);
        }
        notify.setDingtalk(dingtalk);
        config.setNotify(notify);

        ErrorProps error = new ErrorProps();
        error.setThreshold(environment.getProperty("dbsyncer.error.threshold", Integer.class));
        String fatalMatchMode = environment.getProperty("dbsyncer.error.fatal-match-mode", String.class);
        if (fatalMatchMode != null) {
            try {
                error.setFatalMatchMode(org.dbsyncer.common.util.MatchMode.valueOf(fatalMatchMode));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> fatalKeywords = environment.getProperty("dbsyncer.error.fatal-keywords", List.class);
        if (fatalKeywords != null) {
            error.setFatalKeywords(fatalKeywords);
        }
        config.setError(error);

        return config;
    }

    /**
     * 从数据库加载配置。
     */
    private SystemConfig loadFromDatabase() {
        try {
            Query query = new Query();
            query.setType(StorageEnum.CONFIG);
            query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, ConfigConstant.SYSTEM);
            query.setPageNum(1);
            query.setPageSize(1);
            Paging paging = storageService.query(query);
            List<Map> data = (List<Map>) paging.getData();
            if (data == null || data.isEmpty()) {
                return new SystemConfig();
            }
            String json = (String) data.get(0).get(ConfigConstant.CONFIG_MODEL_JSON);
            if (json == null || json.isEmpty()) {
                return new SystemConfig();
            }
            return JsonUtil.jsonToObj(json, SystemConfig.class);
        } catch (Exception e) {
            logger.error("从存储层读取 SystemConfig 失败", e);
            return new SystemConfig();
        }
    }

    /**
     * 合并策略：DB 优先。
     * <p>
     * A 类配置只从 props（DB 不存储）。
     * B/C 类配置：DB 优先，DB 为 null 时回退到 props。
     */
    private SystemConfig merge(SystemConfig props, SystemConfig db) {
        SystemConfig merged = new SystemConfig();

        // A 类：只从 props（DB 不存储）
        merged.setServer(props.getServer());
        merged.setStorageConn(props.getStorageConn());
        merged.setLogging(props.getLogging());
        merged.setHelpdoc(props.getHelpdoc());
        merged.setWorker(props.getWorker());
        merged.setLicense(props.getLicense());

        // B/C 类：DB 优先
        merged.setExecutors(db.getExecutors() != null ? db.getExecutors() : props.getExecutors());
        merged.setBuffers(db.getBuffers() != null ? db.getBuffers() : props.getBuffers());
        merged.setRetry(db.getRetry() != null ? db.getRetry() : props.getRetry());
        merged.setNotify(db.getNotify() != null ? db.getNotify() : props.getNotify());
        merged.setError(db.getError() != null ? db.getError() : props.getError());
        merged.setBasic(db.getBasic() != null ? db.getBasic() : props.getBasic());

        // schemaVersion 从 DB 取
        merged.setSchemaVersion(db.getSchemaVersion());

        return merged;
    }
}
