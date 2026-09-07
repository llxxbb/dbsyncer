package org.dbsyncer.parser.listener;

import org.dbsyncer.common.config.RetryConfig;
import org.dbsyncer.common.config.props.RetryProps;
import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.parser.impl.ConfigRegistry;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RetryConfig 的配置变更适配器（ADR-0030）。
 * <p>
 * RetryConfig 在 dbsyncer-common 中，无法直接实现 ConfigChangeListener，
 * 因此用此适配器桥接。配置统一从 ConfigRegistry 读取（RetryProps，C 类）。
 */
@Component
public class RetryConfigAdapter implements ConfigChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(RetryConfigAdapter.class);

    @Resource
    private RetryConfig retryConfig;

    @Resource
    private ConfigRegistry configRegistry;

    @Override
    public void onConfigChanged(SystemConfig newConfig) {
        // 从 mergedConfig 读取最终配置（DB 优先，Environment 兜底）
        RetryProps retry = configRegistry.getConfig().getRetry();
        if (retry == null) return;

        RetryPolicy global = retryConfig.getGlobal();
        if (retry.getDisable() != null) {
            global.setDisable(retry.getDisable());
        }
        if (retry.getMaxAttempts() != null) {
            global.setMaxAttempts(retry.getMaxAttempts());
        }
        if (retry.getInitialIntervalMs() != null) {
            global.setInitialIntervalMs(retry.getInitialIntervalMs());
        }
        if (retry.getMaxIntervalMs() != null) {
            global.setMaxIntervalMs(retry.getMaxIntervalMs());
        }
        if (retry.getMultiplier() != null) {
            global.setMultiplier(retry.getMultiplier());
        }
        if (retry.getMaxDurationMs() != null) {
            global.setMaxDurationMs(retry.getMaxDurationMs());
        }
        if (retry.getTerminationMode() != null) {
            global.setTerminationMode(retry.getTerminationMode());
        }
        if (retry.getUseKeyword() != null) {
            global.setUseKeyword(retry.getUseKeyword());
        }
        if (retry.getMatchMode() != null) {
            global.setMatchMode(retry.getMatchMode());
        }
        if (retry.getKeywords() != null) {
            global.setKeywords(retry.getKeywords());
        }
        if (retry.getExcludeKeywords() != null) {
            global.setExcludeKeywords(retry.getExcludeKeywords());
        }
        logger.debug("RetryConfig 已更新");
    }
}
