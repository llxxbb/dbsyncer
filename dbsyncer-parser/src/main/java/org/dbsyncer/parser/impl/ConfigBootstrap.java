package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.ConfigEncryptor;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.dbsyncer.common.config.SystemTabEnum;
import org.dbsyncer.common.config.props.ConfigEntry;
import org.dbsyncer.common.config.props.EncryptKeyProps;
import org.dbsyncer.common.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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

    /**
     * 预加载完成后调用。
     * <p>
     * 编排四件事：
     * 1. 初始化加密器（从 EncryptKeyProps 获取密钥）
     * 2. 解密敏感字段
     * 3. 填充 Registry
     * 4. 执行配置迁移
     * 5. 打印配置对照表
     *
     * @param systemConfig 从 ProfileComponent 获取的 SystemConfig（唯一一次查库的产物）
     */
    public void onPreloadCompleted(SystemConfig systemConfig) {
        // 0. 初始化加密器（从 propsConfig 的 EncryptKeyProps 获取密钥）
        EncryptKeyProps encryptKeyProps = configRegistry.getPropsConfig().getEncryptKey();
        ConfigEncryptor encryptor = ConfigEncryptor.fromProps(encryptKeyProps);
        ConfigEncryptor.setInstance(encryptor);

        // 1. 解密敏感字段（如果加密了的话）
        if (encryptor != null && encryptor.isAvailable() && systemConfig != null) {
            systemConfig.decryptSensitiveFields(encryptor);
        }

        // 2. 填充 Registry（DB 数据来自 ProfileComponent，不重复查库）
        configRegistry.init(systemConfig);

        // 3. 配置迁移
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
            EnvironmentLoadable mergedProps = tab.getProps(merged);
            EnvironmentLoadable propsProps = tab.getProps(props);
            EnvironmentLoadable dbProps = tab.getProps(db);
            logger.info("Tab {}: mergedProps={}, dbProps={}, propsProps={}",
                    tab.name(),
                    mergedProps != null ? mergedProps.getClass().getSimpleName() : "null",
                    dbProps != null ? dbProps.getClass().getSimpleName() : "null",
                    propsProps != null ? propsProps.getClass().getSimpleName() : "null");
            if (mergedProps != null) {
                java.util.List<ConfigEntry> testEntries = mergedProps.toConfigEntries();
                logger.info("  merged entries count: {}", testEntries.size());
                if (!testEntries.isEmpty()) {
                    ConfigEntry first = testEntries.get(0);
                    logger.info("  first entry: key={}, value={}", first.getKey(), first.getDisplayValue());
                }
            }
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
