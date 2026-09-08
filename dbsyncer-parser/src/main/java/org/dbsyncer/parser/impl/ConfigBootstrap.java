package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.*;
import org.dbsyncer.common.config.props.ConfigEntry;
import org.dbsyncer.common.config.props.EncryptKeyProps;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * ADR-0029 §2.0.2：启动期配置对照表 + 预加载完成编排。
 * <p>
 * PreloadTemplate 加载完所有 ConfigModel 后，调用 {@link #onPreloadCompleted(SystemConfig)}，
 * 本方法负责：填充 Registry → 迁移 → 打印对照表。
 * <p>
 * 设计：对照表由 {@link SystemTabEnum} 驱动，本类只做格式化。
 */
@Component
public class ConfigBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ConfigBootstrap.class);

    @Resource
    private ConfigRegistry configRegistry;

    @Resource
    private Environment environment;

    @Resource
    private ConfigMigrationService configMigrationService;

    @Lazy
    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    /**
     * 预加载完成后调用。
     * <p>
     * 编排五件事：
     * 0. 初始化加密器（从 EncryptKeyProps 获取密钥）
     * 1. 填充 Registry（首次启动 db 为 null，merge 直接用 props）
     * 2. 首次启动无 DB 记录时，分配 ID 并 INSERT 到 DB
     * 3. 配置迁移（首次启动跳过）
     * 4. 打印配置对照表
     *
     * @param systemConfig 从 ProfileComponent 获取的 SystemConfig（唯一一次查库的产物，可能为 null）
     */
    public void onPreloadCompleted(SystemConfig systemConfig) {
        // 0. 初始化加密器（从 propsConfig 的 EncryptKeyProps 获取密钥）
        EncryptKeyProps encryptKeyProps = configRegistry.getPropsConfig().getEncryptKey();
        ConfigEncryptor encryptor = ConfigEncryptor.fromProps(encryptKeyProps);
        ConfigEncryptor.setInstance(encryptor);

        // 1. 填充 Registry（db 为 null 时 merge 直接返回 props，确保 Registry ready 后 SnowflakeIdWorker 可用）
        configRegistry.init(systemConfig);

        // 2. 首次启动无 DB 记录，分配 ID 并 INSERT
        if (systemConfig == null) {
            logger.info("首次启动：使用 props 初始化配置");
            systemConfig = configRegistry.getConfig();
            systemConfig.setId(String.valueOf(snowflakeIdWorker.nextId()));
            systemConfig.setName(ConfigConstant.SYSTEM);
            long now = Instant.now().toEpochMilli();
            systemConfig.setCreateTime(now);
            systemConfig.setUpdateTime(now);
            systemConfig.setSchemaVersion(ConfigMigrationService.CURRENT_SCHEMA_VERSION);
            try {
                // 深拷贝 + 加密后写入 DB，避免污染内存中的明文配置
                SystemConfig toStore = JsonUtil.jsonToObj(
                        JsonUtil.objToJson(systemConfig), SystemConfig.class);
                if (encryptor != null && encryptor.isAvailable()) {
                    toStore.encryptSensitiveFields(encryptor);
                }
                profileComponent.addConfigModel(toStore);
                logger.info("首次启动：配置已写入数据库");
            } catch (Exception e) {
                logger.error("首次启动：配置写入数据库失败", e);
            }
        } else {
            // 3. 解密敏感字段（如果加密了的话）
            if (encryptor != null && encryptor.isAvailable()) {
                systemConfig.decryptSensitiveFields(encryptor);
            }
        }

        // 3. 配置迁移（首次启动会跳过，因为 schemaVersion 已经是当前版本）
        try {
            configMigrationService.migrateIfNeeded();
        } catch (Exception e) {
            logger.error("配置迁移失败", e);
        }

        // 4. 打印配置对照表
        try {
            printStartupTable();
        } catch (Exception e) {
            logger.error("配置对照表打印失败", e);
        }
    }

    /**
     * 打印配置对照表（由 SystemTabEnum 驱动）。
     */
    private void printStartupTable() {
        SystemConfig props = configRegistry.getPropsConfig();
        SystemConfig db = configRegistry.getDbConfig();
        SystemConfig merged = configRegistry.getConfig();

        if (merged == null || merged.getSchemaVersion() == 0) {
            return;  // 未迁移，不打印
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("  配置对照表（schemaVersion=").append(merged.getSchemaVersion()).append("）\n");
        sb.append("  三源：props（Environment）、db（数据库）、final（合并后，DB 优先）\n");
        
        // 显示加密密钥来源
        ConfigEncryptor encryptor = ConfigEncryptor.getInstance();
        if (encryptor != null && encryptor.isAvailable()) {
            sb.append("  敏感配置加密：已启用（密钥来源：").append(encryptor.getKeySource()).append("）\n");
        } else {
            sb.append("  敏感配置加密：未启用\n");
        }
        
        sb.append("═══════════════════════════════════════════════════════════════════════════════");

        for (SystemTabEnum tab : SystemTabEnum.values()) {
            printSection(sb, tab, props, db, merged);
        }

        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        logger.info(sb.toString());
    }

    /**
     * 打印单个 Tab 的三源对照。
     */
    private void printSection(StringBuilder sb, SystemTabEnum tab,
                              SystemConfig props, SystemConfig db, SystemConfig merged) {
        List<ConfigEntry> mergedEntries = entries(tab.getProps(merged));
        if (mergedEntries.isEmpty()) {
            return;
        }

        List<ConfigEntry> propsEntries = entries(tab.getProps(props));
        List<ConfigEntry> dbEntries = entries(tab.getProps(db));

        sb.append("\n【").append(tab.getTitle()).append("】\n");

        for (ConfigEntry mergedEntry : mergedEntries) {
            String key = mergedEntry.getKey();
            String propsVal = findValue(propsEntries, key);
            String dbVal = findValue(dbEntries, key);
            String finalVal = mergedEntry.getDisplayValue();

            sb.append(String.format("  %s: props=%s, db=%s, final=%s\n",
                    key,
                    propsVal != null ? propsVal : "-",
                    dbVal != null ? dbVal : "-",
                    finalVal));
        }
    }

    private List<ConfigEntry> entries(EnvironmentLoadable props) {
        return props != null ? props.toConfigEntries() : Collections.emptyList();
    }

    /**
     * 从配置条目列表中查找指定 key 的显示值。
     */
    private String findValue(List<ConfigEntry> entries, String key) {
        if (key == null) return null;
        for (ConfigEntry entry : entries) {
            if (key.equals(entry.getKey())) {
                return entry.getDisplayValue();
            }
        }
        return null;
    }
}
