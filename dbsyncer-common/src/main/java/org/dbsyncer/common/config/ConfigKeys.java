package org.dbsyncer.common.config;

/**
 * 全局配置键常量。
 * <p>
 * 所有配置项键名必须引用此类常量，禁止在业务代码中使用字符串字面量。
 * <p>
 * 分组与 application.properties 一致：
 * <ul>
 *   <li>SERVER_* — Spring Boot 服务器配置（A 类，只读）</li>
 *   <li>STORAGE_* — 存储连接配置（A 类，只读）</li>
 *   <li>LOGGING_* — 日志配置（A 类，只读）</li>
 *   <li>HELPDOC_* — 帮助文档配置（A 类，只读）</li>
 *   <li>WORKER_* — Worker 标识配置（A 类，只读）</li>
 *   <li>LICENSE_* — 许可证配置（A 类，只读）</li>
 *   <li>EXECUTOR_* — 执行器线程池配置（B 类，热重载）</li>
 *   <li>BUFFER_* — 缓冲区配置（B 类，热重载）</li>
 *   <li>RETRY_* — 重试策略配置（C 类，立即生效）</li>
 *   <li>NOTIFY_* — 通知通道配置（C 类，立即生效）</li>
 *   <li>ERROR_* — 错误处理配置（C 类，立即生效）</li>
 *   <li>TEMPLATE_* — 模板引擎配置</li>
 *   <li>SQLSERVER_CT_* — SQL Server 增量配置（C 类，立即生效）</li>
 * </ul>
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // ===== A 类：服务器配置（只读，不落库） =====

    public static final String SERVER_PORT = "server.port";
    public static final String SERVER_SSL_ENABLED = "server.ssl.enabled";
    public static final String SERVER_SSL_KEY_STORE = "server.ssl.key-store";
    public static final String SERVER_SSL_KEY_STORE_PASSWORD = "server.ssl.key-store-password";
    public static final String SERVER_SSL_KEY_STORE_TYPE = "server.ssl.key-store-type";
    public static final String SERVER_SSL_KEY_ALIAS = "server.ssl.key-alias";
    public static final String SERVER_HTTP_PORT = "server.http.port";
    public static final String SERVER_HTTP_REDIRECT = "server.http.redirect";
    public static final String SERVER_SERVLET_CONTEXT_PATH = "server.servlet.context-path";
    public static final String SERVER_SERVLET_SESSION_TIMEOUT = "server.servlet.session.timeout";
    /** ServerProps.loadFromEnvironment 使用的非标准 key（无 servlet 段） */
    public static final String SERVER_CONTEXT_PATH = "server.context-path";
    public static final String SERVER_SESSION_TIMEOUT = "server.session-timeout";

    // ===== A 类：加密密钥配置（只读，不落库，脱敏显示） =====

    /**
     * 敏感配置加密密钥。
     * <p>
     * 优先级：系统属性 > 环境变量 > 缺省值（开发环境）。
     * <p>
     * 生产环境务必通过系统属性或环境变量覆盖！
     */
    public static final String SERVER_ENCRYPT_KEY = "dbsyncer.encrypt.key";
    /**
     * 加密密钥来源描述（用于 UI 展示）。
     */
    public static final String SERVER_ENCRYPT_KEY_SOURCE = "dbsyncer.encrypt.key.source";

    // ===== A 类：存储连接配置（只读，不落库） =====

    public static final String STORAGE_PREFIX = "dbsyncer.storage";
    public static final String STORAGE_TYPE = "dbsyncer.storage.type";
    public static final String STORAGE_MYSQL_URL = "dbsyncer.storage.mysql.url";
    public static final String STORAGE_MYSQL_USERNAME = "dbsyncer.storage.mysql.username";
    public static final String STORAGE_MYSQL_PASSWORD = "dbsyncer.storage.mysql.password";
    public static final String STORAGE_MYSQL_DRIVER_CLASS_NAME = "dbsyncer.storage.mysql.driver-class-name";

    // ===== A 类：日志配置（只读，不落库） =====

    public static final String LOGGING_LEVEL_ROOT = "logging.level.root";

    // ===== A 类：帮助文档配置（只读，不落库） =====

    public static final String HELPDOC_PATH = "helpdoc.path";

    // ===== A 类：Worker 标识配置（只读，不落库） =====

    public static final String WORKER_ID = "dbsyncer.web.worker.id";
    public static final String WORKER_DATA_CENTER_ID = "dbsyncer.web.worker.data-center-id";

    // ===== A 类：许可证配置（只读，不落库） =====

    public static final String LICENSE_SERVER_ADDRESS = "dbsyncer.web.license.server.address";

    // ===== C 类：基础设置（立即生效） =====

    public static final String BASIC_PREFIX = "dbsyncer.basic";
    public static final String BASIC_EXPIRE_DATA_DAYS = "dbsyncer.basic.expire-data-days";
    public static final String BASIC_EXPIRE_LOG_DAYS = "dbsyncer.basic.expire-log-days";
    public static final String BASIC_REFRESH_INTERVAL_SECONDS = "dbsyncer.basic.refresh-interval-seconds";
    public static final String BASIC_ENABLE_STORAGE_WRITE_FAIL = "dbsyncer.basic.enable-storage-write-fail";
    public static final String BASIC_MAX_STORAGE_ERROR_LENGTH = "dbsyncer.basic.max-storage-error-length";
    public static final String BASIC_ENABLE_CDN = "dbsyncer.basic.enable-cdn";
    public static final String BASIC_ENABLE_WATERMARK = "dbsyncer.basic.enable-watermark";
    public static final String BASIC_WATERMARK = "dbsyncer.basic.watermark";
    public static final String BASIC_ENABLE_SCHEMA_RESOLVER = "dbsyncer.basic.enable-schema-resolver";
    public static final String BASIC_ENABLE_PRINT_TRACE_INFO = "dbsyncer.basic.enable-print-trace-info";

    // ===== B 类：执行器线程池配置（热重载） =====

    // 通用执行器
    public static final String EXECUTOR_GENERAL_THREAD_CORE_SIZE = "dbsyncer.parser.general.thread-core-size";
    public static final String EXECUTOR_GENERAL_MAX_THREAD_SIZE = "dbsyncer.parser.general.max-thread-size";
    public static final String EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY = "dbsyncer.parser.general.thread-queue-capacity";

    // 存储执行器
    public static final String EXECUTOR_STORAGE_THREAD_CORE_SIZE = "dbsyncer.storage.thread-core-size";
    public static final String EXECUTOR_STORAGE_MAX_THREAD_SIZE = "dbsyncer.storage.max-thread-size";
    public static final String EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY = "dbsyncer.storage.thread-queue-capacity";

    // 调度任务执行器
    public static final String EXECUTOR_DISPATCH_THREAD_CORE_SIZE = "dbsyncer.common.dispatch.thread-core-size";
    public static final String EXECUTOR_DISPATCH_MAX_THREAD_SIZE = "dbsyncer.common.dispatch.max-thread-size";
    public static final String EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY = "dbsyncer.common.dispatch.thread-queue-capacity";

    // ===== B 类：缓冲区配置（热重载） =====

    // 通用缓冲区
    public static final String BUFFER_GENERAL_PULL_COUNT = "dbsyncer.parser.general.buffer-pull-count";
    public static final String BUFFER_GENERAL_QUEUE_CAPACITY = "dbsyncer.parser.general.buffer-queue-capacity";
    public static final String BUFFER_GENERAL_PERIOD_MILLISECOND = "dbsyncer.parser.general.buffer-period-millisecond";

    // ===== C 类：重试策略配置（立即生效） =====

    public static final String RETRY_DISABLE = "dbsyncer.retry.global.disable";
    public static final String RETRY_MAX_ATTEMPTS = "dbsyncer.retry.global.max-attempts";
    public static final String RETRY_INITIAL_INTERVAL_MS = "dbsyncer.retry.global.initial-interval-ms";
    public static final String RETRY_MAX_INTERVAL_MS = "dbsyncer.retry.global.max-interval-ms";
    public static final String RETRY_MULTIPLIER = "dbsyncer.retry.global.multiplier";
    public static final String RETRY_MAX_DURATION_MS = "dbsyncer.retry.global.max-duration-ms";
    public static final String RETRY_USE_KEYWORD = "dbsyncer.retry.global.use-keyword";
    public static final String RETRY_MATCH_MODE = "dbsyncer.retry.global.match-mode";
    public static final String RETRY_KEYWORDS = "dbsyncer.retry.global.keywords";
    public static final String RETRY_EXCLUDE_KEYWORDS = "dbsyncer.retry.global.exclude-keywords";
    public static final String RETRY_TERMINATION_MODE = "dbsyncer.retry.global.termination-mode";

    // ===== C 类：通知通道配置（立即生效） =====

    public static final String NOTIFY_CHANNELS = "dbsyncer.plugin.notify.channels";
    public static final String NOTIFY_MAIL_USERNAME = "dbsyncer.plugin.notify.mail.username";
    public static final String NOTIFY_MAIL_PASSWORD = "dbsyncer.plugin.notify.mail.password";
    public static final String NOTIFY_MAIL_EXCLUDED_NOTIFY_TYPES = "dbsyncer.plugin.notify.mail.excluded-notify-types";
    public static final String NOTIFY_DINGTALK_WEBHOOK_URL = "dbsyncer.plugin.notify.dingtalk.webhook-url";
    public static final String NOTIFY_DINGTALK_SECRET = "dbsyncer.plugin.notify.dingtalk.secret";
    public static final String NOTIFY_DINGTALK_EXCLUDED_NOTIFY_TYPES = "dbsyncer.plugin.notify.dingtalk.excluded-notify-types";

    // ===== C 类：错误处理配置（立即生效） =====

    public static final String ERROR_THRESHOLD = "dbsyncer.error.threshold";
    public static final String ERROR_FATAL_MATCH_MODE = "dbsyncer.error.fatal-match-mode";
    public static final String ERROR_FATAL_KEYWORDS = "dbsyncer.error.fatal-keywords";

    // ===== 模板引擎配置 =====

    public static final String TEMPLATE_MAX_DEPTH = "dbsyncer.template.maxDepth";

    // ===== C 类：SQL Server 增量配置（立即生效） =====

    public static final String SQLSERVER_CT_POLL_INTERVAL_MS = "sqlserver.ct.poll.interval.ms";
    public static final String SQLSERVER_CT_SNAPSHOT_TIME_INTERVAL_MS = "sqlserver.ct.snapshot.time.interval.ms";

    // ===== 监控定时任务配置 =====

    public static final String MONITOR_SCHEDULER_POOL_SIZE = "dbsyncer.monitor.scheduler-pool-size";
    public static final String MONITOR_CPU_MEMORY_INTERVAL_MS = "dbsyncer.monitor.cpu-memory-interval-ms";
    public static final String MONITOR_HEALTH_CHECK_INTERVAL_MS = "dbsyncer.monitor.health-check-interval-ms";
    public static final String MONITOR_CLEANUP_INTERVAL_MS = "dbsyncer.monitor.cleanup-interval-ms";
    public static final String MONITOR_METRIC_REPORT_INTERVAL_MS = "dbsyncer.monitor.metric-report-interval-ms";
    public static final String MONITOR_ALERT_CRON = "dbsyncer.monitor.alert-cron";
}
