package org.dbsyncer.common.config;

import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.function.Supplier;

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
@Lazy
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
    private volatile boolean ready = false;

    /**
     * 业务代码只调用这个方法，获取合并后的最终配置。
     * <p>
     * 未初始化时 fail-fast，把运行期 NPE 变成启动期 IllegalStateException。
     */
    public SystemConfig getConfig() {
        if (!ready) {
            throw new IllegalStateException(
                "ConfigRegistry 尚未初始化，请确保在 init() 完成后再调用 getConfig()。"
                + " 如果在 @PostConstruct / @Bean 中使用，请改用 @Lazy 注入或延迟到运行时读取。");
        }
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
        this.ready = true;
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
     * 合并策略：由 {@link SystemTabEnum} 驱动。
     * <p>
     * A 类只取 Environment，B/C 类 DB 优先、Environment 兜底。
     */
    private SystemConfig merge(SystemConfig props, SystemConfig db) {
        // db 为 null 时（首次启动无 DB 记录），merged 直接等于 props
        if (db == null) {
            return props;
        }

        SystemConfig merged = new SystemConfig();

        for (SystemTabEnum tab : SystemTabEnum.values()) {
            if (tab.isEnvironmentOnly()) {
                tab.setProps(merged, tab.getProps(props));
            } else {
                EnvironmentLoadable dbProps = tab.getProps(db);
                tab.setProps(merged, dbProps != null ? dbProps : tab.getProps(props));
            }
        }

        // schemaVersion 从 DB 取
        merged.setSchemaVersion(db.getSchemaVersion());

        // 反序列化后处理：复合 Props（如 ExecutorProps、BufferProps）重新注入子对象的运行时字段
        for (SystemTabEnum tab : SystemTabEnum.values()) {
            EnvironmentLoadable mergedProps = tab.getProps(merged);
            if (mergedProps != null) {
                mergedProps.afterDeserialize();
            }
        }

        return merged;
    }

    // ===== 配置加载 =====

    /**
     * 从 Environment 加载 A/B/C 类配置。
     * <p>
     * 所有 Props 均实现 {@link EnvironmentLoadable}，此处只做编排：
     * 创建实例 → 调用 loadFromEnvironment → 挂载到 SystemConfig。
     */
    private SystemConfig loadFromEnvironment() {
        SystemConfig config = new SystemConfig();

        // ===== A 类 =====
        config.setServer(load(ServerProps::new));
        config.setStorageConn(load(StorageConnectionProps::new));
        config.setLogging(load(LoggingProps::new));
        config.setHelpdoc(load(HelpdocProps::new));
        config.setWorker(load(WorkerProps::new));
        config.setLicense(load(LicenseProps::new));
        config.setEncryptKey(load(EncryptKeyProps::new));

        // ===== B 类 =====
        config.setExecutors(load(ExecutorProps::new));
        config.setBuffers(load(BufferProps::new));

        // ===== C 类 =====
        config.setBasic(load(BasicProps::new));
        config.setMonitor(load(MonitorProps::new));
        config.setRetry(load(RetryProps::new));
        config.setNotify(load(NotifyProps::new));
        config.setError(load(ErrorProps::new));
        config.setRuntime(load(RuntimeProps::new));
        config.setTemplate(load(TemplateProps::new));

        return config;
    }

    /**
     * 创建 Props 实例并从 Environment 加载配置。
     */
    private <T extends EnvironmentLoadable> T load(Supplier<T> factory) {
        T props = factory.get();
        props.loadFromEnvironment(environment);
        return props;
    }
}
