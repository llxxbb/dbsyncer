package org.dbsyncer.common.config;

import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.List;

/**
 * ADR-0029/0030：SystemConfigRegistry - 三源分离架构 + 配置变更通知。
 * <p>
 * 从 dbsyncer-parser 迁移到 dbsyncer-common，使底层模块可直接访问配置。
 * <p>
 * 持有三个 SystemConfig 实例：
 * <ul>
 *   <li>propsConfig - 从 Spring Environment 加载（所有配置）</li>
 *   <li>dbConfig - 从数据库加载（所有配置）</li>
 *   <li>mergedConfig - 合并后的最终配置（DB 优先，Environment 兜底）</li>
 * </ul>
 * <p>
 * 业务代码只通过 {@link #getConfig()} 获取最终配置。
 * <p>
 * 配置变更时，通过 {@link ConfigChangeListener} 通知所有监听的 Bean，
 * 各 Bean 自行决定如何更新内部状态和清理缓存。
 */
@Component
public class ConfigRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ConfigRegistry.class);

    private Environment environment;

    public ConfigRegistry(Environment environment) {
        this.environment = environment;
        this.propsConfig = loadFromEnvironment();
    }

    // 三个实例
    private SystemConfig propsConfig;   // 从 Environment 加载
    private SystemConfig dbConfig;      // 从数据库加载
    private SystemConfig mergedConfig;  // 合并后的（最终使用）

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

    public synchronized void setDbConfig(SystemConfig newDbConfig) {
        this.dbConfig = newDbConfig;
    }

    /**
     * 设置 DB 配置。
     * <p>
     * 由 ConfigMigrationService 调用，用于首次迁移时把 props 默认值写入 DB。
     * A 类配置不落库，写入前过滤。
     */
    public synchronized void init(SystemConfig dbConfig) {
        this.dbConfig = dbConfig;
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("ConfigRegistry 设置 dbConfig 完成");
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
        logger.info("ConfigRegistry 重新合并完成");
    }

    /**
     * 合并策略：A 类只取 Environment，B/C 类 DB 优先、Environment 兜底。
     */
    private SystemConfig merge(SystemConfig props, SystemConfig db) {
        SystemConfig merged = new SystemConfig();

        // A 类：只从 Environment（不落库）
        merged.setServer(props.getServer());
        merged.setStorageConn(props.getStorageConn());
        merged.setLogging(props.getLogging());
        merged.setHelpdoc(props.getHelpdoc());
        merged.setWorker(props.getWorker());
        merged.setLicense(props.getLicense());

        // B/C 类：DB 优先
        merged.setExecutors(db.getExecutors() != null ? db.getExecutors() : props.getExecutors());
        merged.setBuffers(db.getBuffers() != null ? db.getBuffers() : props.getBuffers());
        merged.setMonitor(db.getMonitor() != null ? db.getMonitor() : props.getMonitor());
        merged.setRetry(db.getRetry() != null ? db.getRetry() : props.getRetry());
        merged.setNotify(db.getNotify() != null ? db.getNotify() : props.getNotify());
        merged.setError(db.getError() != null ? db.getError() : props.getError());
        merged.setBasic(db.getBasic() != null ? db.getBasic() : props.getBasic());
        merged.setRuntime(db.getRuntime() != null ? db.getRuntime() : props.getRuntime());
        merged.setTemplate(db.getTemplate() != null ? db.getTemplate() : props.getTemplate());

        // schemaVersion 从 DB 取
        merged.setSchemaVersion(db.getSchemaVersion());

        return merged;
    }

    // ===== 配置加载 =====

    /**
     * 从 Environment 加载 A/B/C 类配置。
     */
    private SystemConfig loadFromEnvironment() {
        SystemConfig config = new SystemConfig();

        // ===== A 类 =====

        ServerProps server = new ServerProps();
        server.loadFromEnvironment(environment);
        config.setServer(server);

        StorageConnectionProps storageConn = new StorageConnectionProps();
        storageConn.loadFromEnvironment(environment);
        config.setStorageConn(storageConn);

        LoggingProps logging = new LoggingProps();
        logging.loadFromEnvironment(environment);
        config.setLogging(logging);

        HelpdocProps helpdoc = new HelpdocProps();
        helpdoc.loadFromEnvironment(environment);
        config.setHelpdoc(helpdoc);

        WorkerProps worker = new WorkerProps();
        worker.loadFromEnvironment(environment);
        config.setWorker(worker);

        LicenseProps license = new LicenseProps();
        license.loadFromEnvironment(environment);
        config.setLicense(license);

        // ===== B 类（执行器 + 缓冲区） =====

        ExecutorProps executors = new ExecutorProps();
        PoolProps generalPool = new PoolProps();
        generalPool.setThreadCoreSize(environment.getProperty(ConfigKeys.EXECUTOR_GENERAL_THREAD_CORE_SIZE, Integer.class));
        generalPool.setMaxThreadSize(environment.getProperty(ConfigKeys.EXECUTOR_GENERAL_MAX_THREAD_SIZE, Integer.class));
        generalPool.setThreadQueueCapacity(environment.getProperty(ConfigKeys.EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY, Integer.class));
        executors.setGeneral(generalPool);

        PoolProps storagePool = new PoolProps();
        storagePool.setThreadCoreSize(environment.getProperty(ConfigKeys.EXECUTOR_STORAGE_THREAD_CORE_SIZE, Integer.class));
        storagePool.setMaxThreadSize(environment.getProperty(ConfigKeys.EXECUTOR_STORAGE_MAX_THREAD_SIZE, Integer.class));
        storagePool.setThreadQueueCapacity(environment.getProperty(ConfigKeys.EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY, Integer.class));
        executors.setStorage(storagePool);

        PoolProps dispatchPool = new PoolProps();
        dispatchPool.setThreadCoreSize(environment.getProperty(ConfigKeys.EXECUTOR_DISPATCH_THREAD_CORE_SIZE, Integer.class));
        dispatchPool.setMaxThreadSize(environment.getProperty(ConfigKeys.EXECUTOR_DISPATCH_MAX_THREAD_SIZE, Integer.class));
        dispatchPool.setThreadQueueCapacity(environment.getProperty(ConfigKeys.EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY, Integer.class));
        executors.setDispatch(dispatchPool);

        config.setExecutors(executors);

        BufferProps buffers = new BufferProps();
        BufferActuatorProps generalBuffer = new BufferActuatorProps();
        generalBuffer.setBufferWriterCount(environment.getProperty(ConfigKeys.BUFFER_GENERAL_WRITER_COUNT, Integer.class));
        generalBuffer.setBufferPullCount(environment.getProperty(ConfigKeys.BUFFER_GENERAL_PULL_COUNT, Integer.class));
        generalBuffer.setBufferQueueCapacity(environment.getProperty(ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY, Integer.class));
        generalBuffer.setBufferPeriodMillisecond(environment.getProperty(ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND, Integer.class));
        buffers.setGeneral(generalBuffer);

        BufferActuatorProps storageBuffer = new BufferActuatorProps();
        storageBuffer.setBufferWriterCount(environment.getProperty(ConfigKeys.BUFFER_STORAGE_WRITER_COUNT, Integer.class));
        storageBuffer.setBufferPullCount(environment.getProperty(ConfigKeys.BUFFER_STORAGE_PULL_COUNT, Integer.class));
        storageBuffer.setBufferQueueCapacity(environment.getProperty(ConfigKeys.BUFFER_STORAGE_QUEUE_CAPACITY, Integer.class));
        storageBuffer.setBufferPeriodMillisecond(environment.getProperty(ConfigKeys.BUFFER_STORAGE_PERIOD_MILLISECOND, Integer.class));
        buffers.setStorage(storageBuffer);
        config.setBuffers(buffers);

        // ===== C 类（基础 + 重试 + 通知 + 错误 + 运行时 + 模板） =====

        BasicProps basic = new BasicProps();
        basic.loadFromEnvironment(environment);
        config.setBasic(basic);

        MonitorProps monitor = new MonitorProps();
        monitor.setSchedulerPoolSize(environment.getProperty(ConfigKeys.MONITOR_SCHEDULER_POOL_SIZE, Integer.class));
        monitor.setCpuMemoryIntervalMs(environment.getProperty(ConfigKeys.MONITOR_CPU_MEMORY_INTERVAL_MS, Integer.class));
        monitor.setHealthCheckIntervalMs(environment.getProperty(ConfigKeys.MONITOR_HEALTH_CHECK_INTERVAL_MS, Integer.class));
        monitor.setCleanupIntervalMs(environment.getProperty(ConfigKeys.MONITOR_CLEANUP_INTERVAL_MS, Integer.class));
        monitor.setMetricReportIntervalMs(environment.getProperty(ConfigKeys.MONITOR_METRIC_REPORT_INTERVAL_MS, Integer.class));
        monitor.setAlertCron(environment.getProperty(ConfigKeys.MONITOR_ALERT_CRON, String.class));
        config.setMonitor(monitor);

        RetryProps retry = new RetryProps();
        retry.setDisable(environment.getProperty(ConfigKeys.RETRY_DISABLE, Boolean.class));
        retry.setMaxAttempts(environment.getProperty(ConfigKeys.RETRY_MAX_ATTEMPTS, Integer.class));
        retry.setInitialIntervalMs(environment.getProperty(ConfigKeys.RETRY_INITIAL_INTERVAL_MS, Long.class));
        retry.setMaxIntervalMs(environment.getProperty(ConfigKeys.RETRY_MAX_INTERVAL_MS, Long.class));
        retry.setMultiplier(environment.getProperty(ConfigKeys.RETRY_MULTIPLIER, Double.class));
        retry.setMaxDurationMs(environment.getProperty(ConfigKeys.RETRY_MAX_DURATION_MS, Long.class));
        retry.setUseKeyword(environment.getProperty(ConfigKeys.RETRY_USE_KEYWORD, Boolean.class));
        config.setRetry(retry);

        NotifyProps notify = new NotifyProps();
        List<String> notifyChannels = environment.getProperty(ConfigKeys.NOTIFY_CHANNELS, List.class);
        if (notifyChannels != null) {
            notify.setChannels(notifyChannels);
        }
        MailProps mail = new MailProps();
        mail.setUsername(environment.getProperty(ConfigKeys.NOTIFY_MAIL_USERNAME, String.class));
        mail.setPassword(environment.getProperty(ConfigKeys.NOTIFY_MAIL_PASSWORD, String.class));
        List<String> mailNotifyTypes = environment.getProperty(ConfigKeys.NOTIFY_MAIL_NOTIFY_TYPES, List.class);
        if (mailNotifyTypes != null) {
            mail.setNotifyTypes(mailNotifyTypes);
        }
        notify.setMail(mail);
        DingTalkProps dingtalk = new DingTalkProps();
        dingtalk.setWebhookUrl(environment.getProperty(ConfigKeys.NOTIFY_DINGTALK_WEBHOOK_URL, String.class));
        dingtalk.setSecret(environment.getProperty(ConfigKeys.NOTIFY_DINGTALK_SECRET, String.class));
        List<String> dingtalkNotifyTypes = environment.getProperty(ConfigKeys.NOTIFY_DINGTALK_NOTIFY_TYPES, List.class);
        if (dingtalkNotifyTypes != null) {
            dingtalk.setNotifyTypes(dingtalkNotifyTypes);
        }
        notify.setDingtalk(dingtalk);
        config.setNotify(notify);

        ErrorProps error = new ErrorProps();
        error.setThreshold(environment.getProperty(ConfigKeys.ERROR_THRESHOLD, Integer.class));
        String fatalMatchMode = environment.getProperty(ConfigKeys.ERROR_FATAL_MATCH_MODE, String.class);
        if (fatalMatchMode != null) {
            try {
                error.setFatalMatchMode(org.dbsyncer.common.util.MatchMode.valueOf(fatalMatchMode));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> fatalKeywords = environment.getProperty(ConfigKeys.ERROR_FATAL_KEYWORDS, List.class);
        if (fatalKeywords != null) {
            error.setFatalKeywords(fatalKeywords);
        }
        config.setError(error);

        RuntimeProps runtime = new RuntimeProps();
        runtime.loadFromEnvironment(environment);
        config.setRuntime(runtime);

        TemplateProps template = new TemplateProps();
        template.loadFromEnvironment(environment);
        config.setTemplate(template);

        return config;
    }
}
