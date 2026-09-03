package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.sdk.storage.StorageService;
import org.dbsyncer.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * ADR-0029：SystemConfigRegistry - 三源分离架构。
 * <p>
 * 持有三个 SystemConfig 实例：
 * <ul>
 *   <li>propsConfig - 从 Spring Environment 加载（A 类配置）</li>
 *   <li>dbConfig - 从数据库加载（B/C 类配置）</li>
 *   <li>mergedConfig - 合并后的最终配置（DB 优先）</li>
 * </ul>
 * <p>
 * 业务代码只通过 {@link #getConfig()} 获取最终配置。
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
     * DB 配置变更后重新合并。
     * <p>
     * 由 SystemConfigServiceImpl.editTab() 调用。
     */
    public synchronized void onDbConfigChanged(SystemConfig newDbConfig) {
        this.dbConfig = newDbConfig;
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 重新合并完成");
    }

    /**
     * 从 Environment 加载 A 类配置。
     */
    private SystemConfig loadFromEnvironment() {
        SystemConfig config = new SystemConfig();

        // ServerProps
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

        // StorageConnectionProps
        StorageConnectionProps storageConn = new StorageConnectionProps();
        storageConn.setType(environment.getProperty("dbsyncer.storage.type", String.class));
        storageConn.setMysqlUrl(environment.getProperty("dbsyncer.storage.mysql.url", String.class));
        storageConn.setMysqlUsername(environment.getProperty("dbsyncer.storage.mysql.username", String.class));
        storageConn.setMysqlPassword(environment.getProperty("dbsyncer.storage.mysql.password", String.class));
        config.setStorageConn(storageConn);

        // LoggingProps
        LoggingProps logging = new LoggingProps();
        logging.setLevelRoot(environment.getProperty("logging.level.root", String.class));
        config.setLogging(logging);

        // HelpdocProps
        HelpdocProps helpdoc = new HelpdocProps();
        helpdoc.setPath(environment.getProperty("helpdoc.path", String.class));
        config.setHelpdoc(helpdoc);

        // WorkerProps
        WorkerProps worker = new WorkerProps();
        worker.setId(environment.getProperty("dbsyncer.web.worker.id", Long.class));
        worker.setDataCenterId(environment.getProperty("dbsyncer.web.worker.data-center-id", Long.class));
        config.setWorker(worker);

        // LicenseProps
        LicenseProps license = new LicenseProps();
        license.setServerAddress(environment.getProperty("dbsyncer.web.license.server.address", String.class));
        config.setLicense(license);

        return config;
    }

    /**
     * 从数据库加载配置。
     * <p>
     * L2 修订：直接查存储层，不调 getSystemConfig()。
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
