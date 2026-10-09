package org.dbsyncer.parser.impl;

import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import org.springframework.context.annotation.Lazy;

/**
 * ADR-0029 §2.0.1：配置迁移服务。
 * <p>
 * 增量迁移：DB 中的 schemaVersion < {@link #CURRENT_SCHEMA_VERSION} 时，
 * 按版本段逐步升级（v0→v1→v2→...）。
 * <p>
 * 新增迁移步骤：加 migrateVxToVy 方法 + 递增 CURRENT_SCHEMA_VERSION。
 * <p>
 * 本类只负责迁移逻辑，不负责存储和通知。由 SystemConfigServiceImpl 发起调用。
 */
@Component
public class ConfigMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMigrationService.class);

    /**
     * 代码定义的 schema 版本。DB 中的 schemaVersion < 此值时触发迁移。
     * 新增迁移步骤时：加迁移方法 + 递增此常量。
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Resource
    private ConfigRegistry configRegistry;

    @Lazy
    @Resource
    private ProfileComponent profileComponent;

    /**
     * 检测并执行配置迁移。
     */
    public void migrateIfNeeded() throws Exception {
        // 从 Registry 获取合并后的配置（schemaVersion 来自 DB）
        // getConfig() 不会返回 null（merge() 总是创建新对象
        SystemConfig config = configRegistry.getConfig();

        // 委托给迁移服务执行
        boolean migrated = migrate(config);
        if (!migrated) {
            return;
        }

        // 迁移后保存 dbConfig（不是 mergedConfig）
        SystemConfig dbConfig = configRegistry.getDbConfig();
        profileComponent.editConfigModel(dbConfig);
        logger.info("配置迁移完成，dbConfig 已保存到数据库 [id={}]", dbConfig.getId());
    }

    /**
     * 执行迁移。调用方负责判断是否需要迁移、保存和通知。
     *
     * @param config 从 Registry 获取的 mergedConfig，用于判断当前版本
     * @return true 表示执行了迁移，false 表示无需迁移
     */
    public boolean migrate(SystemConfig config) {
        int current = config.getSchemaVersion();
        if (current >= CURRENT_SCHEMA_VERSION) {
            return false;
        }

        logger.info("配置迁移开始：schemaVersion={} → {}", current, CURRENT_SCHEMA_VERSION);

        // 增量迁移：按版本段逐步升级
        if (current < 1) {
            migrateV0ToV1();
        }
        // 未来：if (current < 2) { migrateV1ToV2(config); }

        // 迁移后从 Registry 获取 dbConfig，设置版本号
        SystemConfig dbConfig = configRegistry.getDbConfig();
        dbConfig.setSchemaVersion(CURRENT_SCHEMA_VERSION);

        logger.info("配置迁移完成：schemaVersion={} → {}", current, CURRENT_SCHEMA_VERSION);
        return true;
    }

    /**
     * v0 → v1：首次迁移。把 props 默认值固化到 DB。
     * <p>
     * 注意：必须保留原 dbConfig 的基类字段（id、type、name、createTime、updateTime），
     * 否则后续 editConfigModel 做 UPDATE 时找不到主键。
     */
    private void migrateV0ToV1() {
        logger.info("执行迁移 v0 → v1：把 props 默认值固化到 DB");
        SystemConfig oldDbConfig = configRegistry.getDbConfig();
        SystemConfig propsConfig = configRegistry.getPropsConfig();

        // 深拷贝 props，避免污染 Environment 源数据
        SystemConfig newDbConfig = JsonUtil.jsonToObj(JsonUtil.objToJson(propsConfig), SystemConfig.class);
        // 保留 DB 记录的 identity 字段，否则后续 editConfigModel 找不到主键
        if (oldDbConfig != null) {
            newDbConfig.setId(oldDbConfig.getId());
            newDbConfig.setType(oldDbConfig.getType());
            newDbConfig.setName(oldDbConfig.getName());
            newDbConfig.setCreateTime(oldDbConfig.getCreateTime());
            newDbConfig.setUpdateTime(oldDbConfig.getUpdateTime());
            logger.info("迁移 v0→v1：保留原 DB 记录 [id={}, name={}, createTime={}]",
                    oldDbConfig.getId(), oldDbConfig.getName(), oldDbConfig.getCreateTime());
        }
        configRegistry.setDbConfig(newDbConfig);
    }
}
