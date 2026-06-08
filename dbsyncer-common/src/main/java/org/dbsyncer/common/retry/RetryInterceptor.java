package org.dbsyncer.common.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 统一重试拦截器
 * 支持指数退避、关键字匹配、可配置重试策略
 */
public class RetryInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RetryInterceptor.class);

    /**
     * 执行操作并支持重试（允许操作抛出 Exception）
     *
     * @param operation 待执行的操作
     * @param policy    重试策略
     * @param <T>       返回类型
     * @return 操作结果
     * @throws Exception 重试耗尽后抛出原始异常
     */
    public <T> T execute(ThrowingSupplier<T> operation, RetryPolicy policy) throws Exception {
        return execute(operation, policy, null);
    }

    /**
     * 执行操作并支持重试（允许操作抛出 Exception）
     *
     * @param operation 待执行的操作
     * @param policy    重试策略
     * @param mappingId 映射 ID（用于日志）
     * @param <T>       返回类型
     * @return 操作结果
     * @throws Exception 重试耗尽后抛出原始异常
     */
    public <T> T execute(ThrowingSupplier<T> operation, RetryPolicy policy, String mappingId) throws Exception {
        if (policy.isDisable()) {
            return operation.get();
        }

        Exception lastException = null;
        String lastMatchedKeyword = null;
        long startMs = System.currentTimeMillis();
        int attempt = 0;

        while (true) {
            // 检查次数约束
            if (isAttemptLimitReached(policy, attempt)) {
                break;
            }

            // 检查耗时约束
            if (isDurationExceeded(policy, startMs, attempt)) {
                break;
            }

            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                String message = e.getMessage();

                if (!shouldRetry(e, policy, message)) {
                    throw e;
                }

                long interval = policy.calculateInterval(attempt);
                lastMatchedKeyword = findMatchedKeyword(message, policy);

                String id = mappingId != null ? mappingId : "-";
                logger.warn("触发重试 | mappingId={} | 第{}/{}次 | 间隔={}ms | 关键字={} | 异常={}",
                        id, attempt + 1, getMaxAttemptsDisplay(policy), interval,
                        lastMatchedKeyword != null ? lastMatchedKeyword : "无条件",
                        message);

                if (sleep(interval)) {
                    break;
                }
                attempt++;
            }
        }

        String id = mappingId != null ? mappingId : "-";
        logger.error("重试耗尽 | mappingId={} | 最大重试次数={} | 最终异常={} | 触发关键字={}",
                id, getMaxAttemptsDisplay(policy), lastException.getMessage(), lastMatchedKeyword);
        throw lastException;
    }

    /**
     * 判断次数是否已达上限
     */
    private boolean isAttemptLimitReached(RetryPolicy policy, int attempt) {
        TerminationMode mode = policy.getTerminationMode();
        // MAX_DURATION 模式完全忽略 maxAttempts
        if (mode == TerminationMode.MAX_DURATION) {
            return false;
        }
        return attempt >= policy.getMaxAttempts();
    }

    /**
     * 判断总耗时是否已超限
     */
    private boolean isDurationExceeded(RetryPolicy policy, long startMs, int attempt) {
        if (attempt == 0) {
            return false;  // 首次执行不检查
        }
        TerminationMode mode = policy.getTerminationMode();
        if (mode == TerminationMode.MAX_ATTEMPTS) {
            return false;  // MAX_ATTEMPTS 模式完全忽略 maxDuration
        }
        long maxDuration = policy.getMaxDurationMs();
        if (maxDuration <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - startMs) >= maxDuration;
    }

    /**
     * 获取日志显示的最大次数
     */
    private int getMaxAttemptsDisplay(RetryPolicy policy) {
        TerminationMode mode = policy.getTerminationMode();
        if (mode == TerminationMode.MAX_DURATION) {
            return -1;  // 表示不限次数
        }
        return policy.getMaxAttempts();
    }

    /**
     * 判断异常是否应该重试
     *
     * <p>三段式原则：验证 → 处理 → 返回</p>
     *
     * 逻辑顺序：
     * 1. 验证：检查是否命中排除关键字，命中则不重试
     * 2. 处理：检查是否启用关键字匹配模式
     * 3. 返回：返回重试决策结果
     */
    private boolean shouldRetry(Exception e, RetryPolicy policy, String message) {
        // 验证 1：检查排除关键字（命中则立即抛出，不重试）
        if (policy.isExcludeKeywordMatch(message)) {
            return false;
        }
        // 验证 2：检查是否启用关键字匹配模式
        if (!policy.isUseKeyword()) {
            return true;
        }
        // 处理：检查是否命中重试关键字
        return policy.isKeywordMatch(message);
    }

    /**
     * 查找实际匹配的关键字（用于日志）
     *
     * <p>三段式原则：验证 → 处理 → 返回</p>
     *
     * 返回格式：
     * - 命中排除关键字：返回 "EXCLUDED:" + 关键字
     * - 命中重试关键字：返回关键字
     * - 未命中任何关键字：返回 null
     */
    private String findMatchedKeyword(String message, RetryPolicy policy) {
        // 验证：message 为空时直接返回
        if (message == null) {
            return null;
        }
        
        // 处理 1：优先检查排除关键字
        List<String> excludeKeywords = policy.getExcludeKeywords();
        if (excludeKeywords != null && !excludeKeywords.isEmpty()) {
            KeywordMatcher excludeMatcher = policy.getExcludeMatcher();
            for (String keyword : excludeKeywords) {
                if (keyword != null && excludeMatcher.match(message, keyword)) {
                    return "EXCLUDED:" + keyword;
                }
            }
        }
        
        // 处理 2：再检查重试关键字
        List<String> keywords = policy.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        KeywordMatcher matcher = policy.getMatcher();
        for (String keyword : keywords) {
            if (keyword != null && matcher.match(message, keyword)) {
                return keyword;
            }
        }
        // 返回：未命中任何关键字
        return null;
    }

    /**
     * 休眠指定毫秒数
     *
     * @return true 表示被中断，false 表示正常完成休眠
     */
    private boolean sleep(long interval) {
        try {
            Thread.sleep(interval);
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
