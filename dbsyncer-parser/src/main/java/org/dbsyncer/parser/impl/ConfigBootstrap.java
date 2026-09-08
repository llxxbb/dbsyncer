package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.dbsyncer.common.config.props.ConfigEntry;
import org.dbsyncer.common.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ADR-0029 §2.0.2：启动期配置对照表 + 预加载完成编排。
 * <p>
 * PreloadTemplate 加载完所有 ConfigModel 后，调用 {@link #onPreloadCompleted(SystemConfig)}，
 * 本方法负责：填充 Registry → 迁移 → 打印对照表。
 * <p>
 * 设计：各 Props 子类实现 {@link EnvironmentLoadable} 接口提供 entries，
 * 本类负责三源对照的编排和格式化。
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
     * 编排三件事：
     * 1. 将 ProfileComponent 中的 SystemConfig 填充到 Registry（不重复查库）
     * 2. 执行配置迁移
     * 3. 打印配置对照表
     *
     * @param systemConfig 从 ProfileComponent 获取的 SystemConfig（唯一一次查库的产物）
     */
    public void onPreloadCompleted(SystemConfig systemConfig) {
        // 1. 填充 Registry（DB 数据来自 ProfileComponent，不重复查库）
        configRegistry.init(systemConfig);

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
     * 配置分区：标题 + 三源 Props 实例。
     */
    private static class Section {
        final String title;
        final EnvironmentLoadable props;
        final EnvironmentLoadable db;
        final EnvironmentLoadable merged;

        Section(String title, EnvironmentLoadable props, EnvironmentLoadable db, EnvironmentLoadable merged) {
            this.title = title;
            this.props = props;
            this.db = db;
            this.merged = merged;
        }
    }

    /**
     * 打印配置对照表（按子类分组）。
     * <p>
     * 声明式编排：定义分区列表，循环打印。
     */
    private void printStartupTable() {
        SystemConfig props = configRegistry.getPropsConfig();
        SystemConfig db = configRegistry.getDbConfig();
        SystemConfig merged = configRegistry.getConfig();

        if (merged == null || merged.getSchemaVersion() == 0) {
            return;  // 未迁移，不打印
        }

        List<Section> sections = buildSections(props, db, merged);
        String table = formatTable(merged.getSchemaVersion(), sections);
        logger.info(table);
    }

    /**
     * 构建配置分区列表（声明式）。
     */
    private List<Section> buildSections(SystemConfig props, SystemConfig db, SystemConfig merged) {
        List<Section> sections = new ArrayList<>();

        // A 类：只读配置
        sections.add(new Section("A类：服务器配置（只读，不落库）",
                props.getServer(), db.getServer(), merged.getServer()));
        sections.add(new Section("A类：存储连接配置（只读，不落库）",
                props.getStorageConn(), db.getStorageConn(), merged.getStorageConn()));
        sections.add(new Section("A类：日志配置（只读，不落库）",
                props.getLogging(), db.getLogging(), merged.getLogging()));
        sections.add(new Section("A类：帮助文档配置（只读，不落库）",
                props.getHelpdoc(), db.getHelpdoc(), merged.getHelpdoc()));
        sections.add(new Section("A类：Worker配置（只读，不落库）",
                props.getWorker(), db.getWorker(), merged.getWorker()));
        sections.add(new Section("A类：许可证配置（只读，不落库）",
                props.getLicense(), db.getLicense(), merged.getLicense()));

        // 基础配置
        sections.add(new Section("基础配置",
                props.getBasic(), db.getBasic(), merged.getBasic()));

        // B 类：需重启配置
        sections.add(new Section("B类：执行器配置",
                props.getExecutors(), db.getExecutors(), merged.getExecutors()));
        sections.add(new Section("B类：缓冲区配置",
                props.getBuffers(), db.getBuffers(), merged.getBuffers()));

        // C 类：立即生效配置
        sections.add(new Section("C类：监控定时任务配置",
                props.getMonitor(), db.getMonitor(), merged.getMonitor()));
        sections.add(new Section("C类：重试策略配置",
                props.getRetry(), db.getRetry(), merged.getRetry()));
        sections.add(new Section("C类：错误处理配置",
                props.getError(), db.getError(), merged.getError()));
        sections.add(new Section("C类：通知通道配置",
                props.getNotify(), db.getNotify(), merged.getNotify()));
        sections.add(new Section("C类：运行时控制配置",
                props.getRuntime(), db.getRuntime(), merged.getRuntime()));
        sections.add(new Section("C类：模板引擎配置",
                props.getTemplate(), db.getTemplate(), merged.getTemplate()));

        return sections;
    }

    /**
     * 格式化三源对照表。
     */
    private String formatTable(int schemaVersion, List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("  配置对照表（schemaVersion=").append(schemaVersion).append("）\n");
        sb.append("  三源：props（Environment）、db（数据库）、final（合并后，DB 优先）\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════");

        for (Section section : sections) {
            formatSection(sb, section);
        }

        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    /**
     * 格式化单个分区的三源对照。
     */
    private void formatSection(StringBuilder sb, Section section) {
        List<ConfigEntry> mergedEntries = entries(section.merged);
        if (mergedEntries.isEmpty()) {
            return;
        }

        List<ConfigEntry> propsEntries = entries(section.props);
        List<ConfigEntry> dbEntries = entries(section.db);

        sb.append("\n【").append(section.title).append("】\n");

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
        for (ConfigEntry entry : entries) {
            if (entry.getKey().equals(key)) {
                return entry.getDisplayValue();
            }
        }
        return null;
    }
}
