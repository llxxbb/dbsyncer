package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;

/**
 * ADR-0029 §2.0.2：启动期配置对照表。
 * <p>
 * 在 PreloadTemplate.onApplicationEvent 完成后、openStartupGates() 之前打印。
 * 便于排查配置来源和最终值。
 * <p>
 * 设计：各 Props 子类自行实现 toConfigEntries()，本类只做编排和格式化。
 */
@Component
public class ConfigComparator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigComparator.class);

    @Resource
    private OperationTemplate operationTemplate;

    @Resource
    private Environment environment;

    /**
     * 打印配置对照表（按子类分组）。
     * <p>
     * ADR-0029 L2：启动期直接查存储层，避免触碰业务层方法（如 getSystemConfig()）。
     */
    public void printStartupTable() {
        SystemConfig config = querySystemConfigDirect();
        if (config == null || config.getSchemaVersion() == 0) {
            return;  // 未迁移，不打印
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("  配置对照表（schemaVersion=").append(config.getSchemaVersion()).append("）\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════");

        // A 类：服务器配置（只读，从 Environment 直接读取）
        printAclassSection(sb, "A类：服务器配置（只读，不落库）", new ServerProps());

        // B 类：执行器
        printSection(sb, "B类：执行器配置", 
                config.getExecutors() != null ? config.getExecutors().toConfigEntries() : null);

        // B 类：缓冲区
        printSection(sb, "B类：缓冲区配置", 
                config.getBuffers() != null ? config.getBuffers().toConfigEntries() : null);

        // B 类：通知通道
        printSection(sb, "B类：通知通道配置", 
                config.getNotify() != null ? config.getNotify().toConfigEntries() : null);

        // C 类：重试策略
        printSection(sb, "C类：重试策略配置", 
                config.getRetry() != null ? config.getRetry().toConfigEntries() : null);

        // C 类：错误处理
        printSection(sb, "C类：错误处理配置", 
                config.getError() != null ? config.getError().toConfigEntries() : null);

        // D 类：基础配置
        printSection(sb, "D类：基础配置", 
                config.getBasic() != null ? config.getBasic().toConfigEntries() : null);

        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        logger.info(sb.toString());
    }

    /**
     * 打印一个配置分区。
     */
    private void printSection(StringBuilder sb, String title, List<ConfigEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        sb.append("\n【").append(title).append("】\n");

        for (ConfigEntry entry : entries) {
            String propsVal = environment.getProperty(entry.getKey());
            String dbVal = entry.getDisplayValue();
            String finalVal = dbVal;  // ADR-0029 后 DB 值就是最终值

            sb.append(String.format("%s: %s, %s, %s\n",
                    entry.getKey(),
                    propsVal != null ? propsVal : "-",
                    dbVal,
                    finalVal));
        }
    }

    /**
     * 打印 A 类配置分区（从 Environment 直接读取，不落库）。
     */
    private void printAclassSection(StringBuilder sb, String title, Object props) {
        if (props == null) {
            return;
        }

        List<ConfigEntry> entries = null;
        if (props instanceof ServerProps) {
            entries = ((ServerProps) props).toConfigEntries();
        }

        if (entries == null || entries.isEmpty()) {
            return;
        }

        sb.append("\n【").append(title).append("】\n");

        for (ConfigEntry entry : entries) {
            String propsVal = environment.getProperty(entry.getKey());
            // A 类配置：props 值就是最终值，DB 值显示为 "-"（不落库）
            String finalVal = propsVal != null ? propsVal : "-";

            sb.append(String.format("%s: %s, -, %s\n",
                    entry.getKey(),
                    propsVal != null ? propsVal : "-",
                    finalVal));
        }
    }

    /**
     * 直接查存储层获取 SystemConfig，避免触发业务层的默认创建逻辑。
     * ADR-0029 L2 修订：启动期绝对禁止触碰 getSystemConfig()。
     */
    private SystemConfig querySystemConfigDirect() {
        List<SystemConfig> list = operationTemplate.queryAll(SystemConfig.class);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        // 多条：确定性选择（createTime 最早，平手取 id 最小）
        logger.warn("[系统配置] 检测到 {} 个重复配置，将选择 createTime 最早的配置", list.size());
        return list.stream()
            .min(Comparator.comparing(SystemConfig::getCreateTime)
                .thenComparing(SystemConfig::getId))
            .orElse(list.get(0));
    }
}
