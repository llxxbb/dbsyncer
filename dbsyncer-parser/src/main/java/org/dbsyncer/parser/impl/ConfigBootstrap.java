package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * ADR-0029 §2.0.2：启动期配置对照表 + 预加载完成编排。
 * <p>
 * PreloadTemplate 加载完所有 ConfigModel 后，调用 {@link #onPreloadCompleted(SystemConfig)}，
 * 本方法负责：填充 Registry → 迁移 → 打印对照表。
 * <p>
 * 设计：各 Props 子类自行实现 toConfigEntries()，本类只做编排和格式化。
 */
@Component
public class ConfigBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(ConfigBootstrap.class);

    @Resource
    private SystemConfigRegistry configRegistry;

    @Resource
    private Environment environment;

    @Resource
    private ConfigMigrationService configMigrationService;

    /**
     * 预加载完成后调用。
     * <p>
     * 编排三件事：
     * 1. 将 ProfileComponent 中的 SystemConfig 填充到 Registry（不重复查库）
     * 2. 执行配置迁移
     * 3. 打印配置对照表
     *
     * @param systemConfig 从 ProfileComponent 获取的 SystemConfig（唯一一次查库的产物）
     */
    public void onPreloadCompleted(SystemConfig systemConfig) {
        // 1. 填充 Registry（DB 数据来自 ProfileComponent，不重复查库）
        configRegistry.setDbConfig(systemConfig);

        // 2. 配置迁移
        try {
            configMigrationService.migrateIfNeeded();
        } catch (Exception e) {
            logger.error("配置迁移失败", e);
        }

        // 3. 打印配置对照表
        try {
            printStartupTable();
        } catch (Exception e) {
            logger.error("配置对照表打印失败", e);
        }
    }

    /**
     * 打印配置对照表（按子类分组）。
     * <p>
     * 使用 SystemConfigRegistry 获取三源配置：propsConfig、dbConfig、mergedConfig。
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
        sb.append("═══════════════════════════════════════════════════════════════════════════════");

        // A 类：服务器配置（只读）
        printSection(sb, "A类：服务器配置（只读，不落库）", 
                props.getServer() != null ? props.getServer().toConfigEntries() : null,
                db.getServer() != null ? db.getServer().toConfigEntries() : null,
                merged.getServer() != null ? merged.getServer().toConfigEntries() : null);

        // B 类：执行器
        printSection(sb, "B类：执行器配置", 
                props.getExecutors() != null ? props.getExecutors().toConfigEntries() : null,
                db.getExecutors() != null ? db.getExecutors().toConfigEntries() : null,
                merged.getExecutors() != null ? merged.getExecutors().toConfigEntries() : null);

        // B 类：缓冲区
        printSection(sb, "B类：缓冲区配置", 
                props.getBuffers() != null ? props.getBuffers().toConfigEntries() : null,
                db.getBuffers() != null ? db.getBuffers().toConfigEntries() : null,
                merged.getBuffers() != null ? merged.getBuffers().toConfigEntries() : null);

        // B 类：通知通道
        printSection(sb, "B类：通知通道配置", 
                props.getNotify() != null ? props.getNotify().toConfigEntries() : null,
                db.getNotify() != null ? db.getNotify().toConfigEntries() : null,
                merged.getNotify() != null ? merged.getNotify().toConfigEntries() : null);

        // C 类：重试策略
        printSection(sb, "C类：重试策略配置", 
                props.getRetry() != null ? props.getRetry().toConfigEntries() : null,
                db.getRetry() != null ? db.getRetry().toConfigEntries() : null,
                merged.getRetry() != null ? merged.getRetry().toConfigEntries() : null);

        // C 类：错误处理
        printSection(sb, "C类：错误处理配置", 
                props.getError() != null ? props.getError().toConfigEntries() : null,
                db.getError() != null ? db.getError().toConfigEntries() : null,
                merged.getError() != null ? merged.getError().toConfigEntries() : null);

        // C 类：基础配置
        printSection(sb, "C类：基础配置", 
                props.getBasic() != null ? props.getBasic().toConfigEntries() : null,
                db.getBasic() != null ? db.getBasic().toConfigEntries() : null,
                merged.getBasic() != null ? merged.getBasic().toConfigEntries() : null);

        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        logger.info(sb.toString());
    }

    /**
     * 打印一个配置分区（三源对照）。
     */
    private void printSection(StringBuilder sb, String title, 
                              List<ConfigEntry> propsEntries,
                              List<ConfigEntry> dbEntries,
                              List<ConfigEntry> mergedEntries) {
        if (mergedEntries == null || mergedEntries.isEmpty()) {
            return;
        }

        sb.append("\n【").append(title).append("】\n");

        for (ConfigEntry mergedEntry : mergedEntries) {
            String key = mergedEntry.getKey();
            
            // 从三源中查找对应的值
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

    /**
     * 从配置条目列表中查找指定 key 的值。
     */
    private String findValue(List<ConfigEntry> entries, String key) {
        if (entries == null) {
            return null;
        }
        for (ConfigEntry entry : entries) {
            if (entry.getKey().equals(key)) {
                return entry.getDisplayValue();
            }
        }
        return null;
    }
}
