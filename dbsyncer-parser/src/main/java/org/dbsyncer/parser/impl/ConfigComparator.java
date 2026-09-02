package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.parser.impl.OperationTemplate;
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
 */
@Component
public class ConfigComparator {

    private static final Logger logger = LoggerFactory.getLogger(ConfigComparator.class);

    @Resource
    private OperationTemplate operationTemplate;

    @Resource
    private Environment environment;

    /**
     * 打印配置对照表。
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
        sb.append("╔══════════════════════════════════════╦═════════╦═════════╦═══════╗\n");
        sb.append("║ 配置对照（schemaVersion=").append(config.getSchemaVersion()).append("）");
        // 填充空格对齐
        for (int i = 0; i < 14; i++) sb.append(' ');
        sb.append("║\n");
        sb.append("╠══════════════════════════════════════╬═════════╬═════════╬═══════╣\n");
        sb.append("║ 配置项                               ║ props   ║ DB      ║ 最终  ║\n");
        sb.append("╠══════════════════════════════════════╬═════════╬═════════╬═══════╣\n");

        // B 类：执行器
        ExecutorProps exec = config.getExecutors();
        if (exec != null) {
            printPoolRows(sb, "dbsyncer.parser.general", exec.getGeneral());
            printPoolRows(sb, "dbsyncer.storage", exec.getStorage());
            printPoolRows(sb, "dbsyncer.common.dispatch", exec.getDispatch());
            if (exec.getSchedulerPoolSize() != null) {
                printRow(sb, "dbsyncer.web.scheduler.pool-size", null,
                        String.valueOf(exec.getSchedulerPoolSize()), String.valueOf(exec.getSchedulerPoolSize()));
            }
        }

        // B 类：缓冲区
        BufferProps buf = config.getBuffers();
        if (buf != null) {
            printBufferRows(sb, "dbsyncer.parser.general", buf.getGeneral());
            printBufferRows(sb, "dbsyncer.storage", buf.getStorage());
        }

        // C 类：重试策略
        RetryProps retry = config.getRetry();
        if (retry != null) {
            printRow(sb, "dbsyncer.retry.global.max-attempts",
                    environment.getProperty("dbsyncer.retry.global.max-attempts"),
                    retry.getMaxAttempts() != null ? String.valueOf(retry.getMaxAttempts()) : "-",
                    retry.getMaxAttempts() != null ? String.valueOf(retry.getMaxAttempts()) : environment.getProperty("dbsyncer.retry.global.max-attempts", "-"));
        }

        sb.append("╚══════════════════════════════════════╩═════════╩═════════╩═══════╝\n");
        logger.info(sb.toString());
    }

    private void printPoolRows(StringBuilder sb, String prefix, PoolProps pool) {
        if (pool == null) return;
        printRow(sb, prefix + ".thread-core-size",
                environment.getProperty(prefix + ".thread-core-size"),
                pool.getThreadCoreSize() != null ? String.valueOf(pool.getThreadCoreSize()) : "-",
                pool.getThreadCoreSize() != null ? String.valueOf(pool.getThreadCoreSize()) : environment.getProperty(prefix + ".thread-core-size", "-"));
        printRow(sb, prefix + ".max-thread-size",
                environment.getProperty(prefix + ".max-thread-size"),
                pool.getMaxThreadSize() != null ? String.valueOf(pool.getMaxThreadSize()) : "-",
                pool.getMaxThreadSize() != null ? String.valueOf(pool.getMaxThreadSize()) : environment.getProperty(prefix + ".max-thread-size", "-"));
        printRow(sb, prefix + ".thread-queue-capacity",
                environment.getProperty(prefix + ".thread-queue-capacity"),
                pool.getThreadQueueCapacity() != null ? String.valueOf(pool.getThreadQueueCapacity()) : "-",
                pool.getThreadQueueCapacity() != null ? String.valueOf(pool.getThreadQueueCapacity()) : environment.getProperty(prefix + ".thread-queue-capacity", "-"));
    }

    private void printBufferRows(StringBuilder sb, String prefix, BufferActuatorProps buf) {
        if (buf == null) return;
        printRow(sb, prefix + ".buffer-queue-capacity",
                environment.getProperty(prefix + ".buffer-queue-capacity"),
                buf.getBufferQueueCapacity() != null ? String.valueOf(buf.getBufferQueueCapacity()) : "-",
                buf.getBufferQueueCapacity() != null ? String.valueOf(buf.getBufferQueueCapacity()) : environment.getProperty(prefix + ".buffer-queue-capacity", "-"));
        printRow(sb, prefix + ".buffer-period-millisecond",
                environment.getProperty(prefix + ".buffer-period-millisecond"),
                buf.getBufferPeriodMillisecond() != null ? String.valueOf(buf.getBufferPeriodMillisecond()) : "-",
                buf.getBufferPeriodMillisecond() != null ? String.valueOf(buf.getBufferPeriodMillisecond()) : environment.getProperty(prefix + ".buffer-period-millisecond", "-"));
    }

    private void printRow(StringBuilder sb, String key, String propsVal, String dbVal, String finalVal) {
        sb.append(String.format("║ %-36s ║ %-7s ║ %-7s ║ %-5s ║\n",
                truncate(key, 36),
                propsVal != null ? truncate(propsVal, 7) : "-",
                dbVal != null ? truncate(dbVal, 7) : "-",
                finalVal != null ? truncate(finalVal, 5) : "-"));
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "-";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
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
