/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.common.config;

import org.dbsyncer.common.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

/**
 * 重试配置绑定类
 * 支持全局默认配置和任务级覆盖
 *
 * <p>配置示例：</p>
 * <pre>
 * # 全局默认配置
 * dbsyncer.retry.global.use-keyword=false
 * dbsyncer.retry.global.initial-interval=1000
 * dbsyncer.retry.global.max-interval=60000
 * dbsyncer.retry.global.max-attempts=5
 * dbsyncer.retry.global.multiplier=2.0
 *
 * # 任务级覆盖（优先级高于全局）
 * dbsyncer.retry.task.1001.use-keyword=true
 * dbsyncer.retry.task.1001.initial-interval=500
 * dbsyncer.retry.task.1001.max-attempts=10
 *
 * # 完全禁用重试
 * dbsyncer.retry.task.2002.disable=true
 * </pre>
 *
 * @author DBSyncer
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "dbsyncer.retry")
public class RetryConfig {

    private static final Logger logger = LoggerFactory.getLogger(RetryConfig.class);

    /** 全局默认重试策略 */
    private RetryPolicy global = new RetryPolicy();

    /** 任务级重试策略覆盖（key 为任务 ID） */
    private Map<String, RetryPolicy> task = new HashMap<>();

    /**
     * 获取指定任务的重试策略
     * 如果任务有独立配置则返回任务级策略，否则返回全局策略
     *
     * @param taskId 任务 ID
     * @return 重试策略
     */
    public RetryPolicy getPolicy(String taskId) {
        if (taskId != null && task.containsKey(taskId)) {
            return task.get(taskId);
        }
        return global;
    }

    public RetryPolicy getGlobal() {
        return global;
    }

    public void setGlobal(RetryPolicy global) {
        this.global = global;
    }

    public Map<String, RetryPolicy> getTask() {
        return task;
    }

    public void setTask(Map<String, RetryPolicy> task) {
        this.task = task;
    }

    @PostConstruct
    public void init() {
        printConfig();
    }

    /**
     * 打印重试配置信息
     */
    private void printConfig() {
        logger.info("========== 重试配置加载完成 ==========");
        logger.info("全局配置:");
        logger.info("  disable: {}", global.isDisable());
        logger.info("  maxAttempts: {}", global.getMaxAttempts());
        logger.info("  initialIntervalMs: {}", global.getInitialIntervalMs());
        logger.info("  maxIntervalMs: {}", global.getMaxIntervalMs());
        logger.info("  multiplier: {}", global.getMultiplier());
        logger.info("  maxDurationMs: {}", global.getMaxDurationMs());
        logger.info("  terminationMode: {}", global.getTerminationMode());
        logger.info("  useKeyword: {}", global.isUseKeyword());
        logger.info("  matchMode: {}", global.getMatchMode());
        logger.info("  keywords: {}", global.getKeywords());
        logger.info("  excludeKeywords: {}", global.getExcludeKeywords());

        if (task != null && !task.isEmpty()) {
            logger.info("任务级配置 ({} 个任务):", task.size());
            task.forEach((id, policy) -> {
                logger.info("  任务 {}:", id);
                logger.info("    disable: {}", policy.isDisable());
                logger.info("    maxAttempts: {}", policy.getMaxAttempts());
                logger.info("    initialIntervalMs: {}", policy.getInitialIntervalMs());
                logger.info("    maxIntervalMs: {}", policy.getMaxIntervalMs());
                logger.info("    multiplier: {}", policy.getMultiplier());
                logger.info("    maxDurationMs: {}", policy.getMaxDurationMs());
                logger.info("    terminationMode: {}", policy.getTerminationMode());
                logger.info("    useKeyword: {}", policy.isUseKeyword());
                logger.info("    matchMode: {}", policy.getMatchMode());
                logger.info("    keywords: {}", policy.getKeywords());
                logger.info("    excludeKeywords: {}", policy.getExcludeKeywords());
            });
        } else {
            logger.info("任务级配置: 无");
        }
        logger.info("=======================================");
    }
}
