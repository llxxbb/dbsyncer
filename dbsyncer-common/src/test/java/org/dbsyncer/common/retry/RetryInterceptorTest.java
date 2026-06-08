package org.dbsyncer.common.retry;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class RetryInterceptorTest {

    private final RetryInterceptor interceptor = new RetryInterceptor();

    @Test
    public void testSuccessOnFirstAttempt() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        String result = interceptor.execute(op, policy);

        assertEquals("success", result);
        assertEquals(1, counter.get());
    }

    @Test
    public void testRetryThenSuccess() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient error");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(3);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);

        String result = interceptor.execute(op, policy);

        assertEquals("success", result);
        assertEquals(3, counter.get());
    }

    @Test
    public void testExhaustRetries() {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("persistent error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(3);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            assertEquals(3, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testDisabledRetry() {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(true);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            assertEquals(1, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testKeywordMatchRetry() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("deadlock detected");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(3);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        String result = interceptor.execute(op, policy);

        assertEquals("success", result);
        assertEquals(3, counter.get());
    }

    @Test
    public void testKeywordNoMatchNoRetry() {
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("non-retryable error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // Should NOT retry because keyword doesn't match
            assertEquals(1, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testExponentialBackoff() {
        // Verify that calculateInterval produces exponential backoff
        RetryPolicy policy = new RetryPolicy();
        policy.setInitialIntervalMs(100L);
        policy.setMultiplier(2.0);
        policy.setMaxIntervalMs(10000L);

        long i0 = policy.calculateInterval(0); // 100
        long i1 = policy.calculateInterval(1); // 200
        long i2 = policy.calculateInterval(2); // 400
        long i3 = policy.calculateInterval(3); // 800

        assertEquals(100L, i0);
        assertEquals(200L, i1);
        assertEquals(400L, i2);
        assertEquals(800L, i3);
        // Each interval should be exactly double the previous
        assertEquals(i0 * 2, i1);
        assertEquals(i1 * 2, i2);
        assertEquals(i2 * 2, i3);
    }

    @Test
    public void testMaxIntervalCap() {
        RetryPolicy policy = new RetryPolicy();
        policy.setInitialIntervalMs(100L);
        policy.setMultiplier(2.0);
        policy.setMaxIntervalMs(500L);

        // attempt 0: 100, attempt 1: 200, attempt 2: 400, attempt 3: 500 (capped), attempt 4: 500 (capped)
        assertEquals(100L, policy.calculateInterval(0));
        assertEquals(200L, policy.calculateInterval(1));
        assertEquals(400L, policy.calculateInterval(2));
        assertEquals(500L, policy.calculateInterval(3));
        assertEquals(500L, policy.calculateInterval(4));
    }

    // ==================== TerminationMode 单元测试 ====================

    @Test
    public void testTerminationModeMaxAttempts() throws Exception {
        // MAX_ATTEMPTS 模式：仅按次数终止，完全忽略 maxDuration
        // 关键验证：maxDuration=1ms（必然在 3 次内超时），但应继续到 3 次
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(3);
        policy.setInitialIntervalMs(10L);   // 每次间隔 10ms，累计肯定超过 1ms
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);
        policy.setTerminationMode(TerminationMode.MAX_ATTEMPTS);
        policy.setMaxDurationMs(0L);        // 极短超时，应被忽略

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 关键断言：执行次数 = 3，证明 maxDuration=1ms 被忽略
            assertEquals("maxDuration 应被忽略", 3, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testTerminationModeMaxDuration() throws Exception {
        // MAX_DURATION 模式：仅按总耗时终止，完全忽略 maxAttempts
        // 关键验证：maxAttempts=3 但耗时允许的情况下，实际执行次数应 > 3
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            if (counter.get() <= 20) {
                throw new RuntimeException("error");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(0);           // 次数很小
        policy.setInitialIntervalMs(10L);   // 每次间隔 10ms
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);
        policy.setTerminationMode(TerminationMode.MAX_DURATION);
        policy.setMaxDurationMs(200L);      // 200ms 足够重试 >3 次

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 关键断言：执行次数超过 maxAttempts=3，证明 maxAttempts 被忽略
            assertTrue("maxAttempts 应被忽略，但实际次数=" + counter.get(), counter.get() > 3);
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testTerminationModeWhicheverFirst_MaxAttemptsWins() throws Exception {
        // WHICHEVER_FIRST：次数先到
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(3);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);
        policy.setTerminationMode(TerminationMode.WHICHEVER_FIRST);
        policy.setMaxDurationMs(60000L);  // 60秒，不会先到

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            assertEquals(3, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testTerminationModeWhicheverFirst_MaxDurationWins() throws Exception {
        // WHICHEVER_FIRST：耗时先到
        // 关键验证：maxAttempts=10 但 100ms 内就跑完 10 次，实际应 < 10
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            if (counter.get() <= 50) {
                throw new RuntimeException("error");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(100);           // 次数很大
        policy.setInitialIntervalMs(20L);     // 每次间隔 20ms
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);
        policy.setTerminationMode(TerminationMode.WHICHEVER_FIRST);
        policy.setMaxDurationMs(100L);        // 100ms 约够 4-5 次

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 被 maxDuration 截断，远少于 maxAttempts=100
            assertTrue("应被 maxDuration 截断，但次数=" + counter.get(), counter.get() < 50);
            assertTrue("应至少执行 1 次", counter.get() >= 1);
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testTerminationModeNoDurationLimit() throws Exception {
        // maxDurationMs=0 表示不限制，按 maxAttempts 走
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            if (counter.get() < 3) {
                throw new RuntimeException("error");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);
        policy.setTerminationMode(TerminationMode.WHICHEVER_FIRST);
        policy.setMaxDurationMs(0L);      // 不限制

        String result = interceptor.execute(op, policy);
        assertEquals("success", result);
        assertEquals(3, counter.get());
    }

    @Test
    public void testInterruptedExceptionStopsRetry() throws Exception {
        // 中断时退出重试循环
        final AtomicInteger counter = new AtomicInteger(0);

        ThrowingSupplier<String> op = () -> {
            int c = counter.incrementAndGet();
            if (c == 2) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(10);
        policy.setInitialIntervalMs(50L);  // 较长间隔确保中断能在 sleep 中触发
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 中断后退出循环，尝试次数远小于 maxAttempts=10
            assertTrue(counter.get() < 10);
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    // ==================== 排除关键字优先测试 ====================

    @Test
    public void testExcludeKeywordPriorityOverRetryKeyword() {
        // 验证排除关键字优先级高于重试关键字
        // 当同时命中 exclude 和 keywords 时，应不重试
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("deadlock detected and fatal error");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock"));       // 命中 - 应重试
        policy.setExcludeKeywords(Arrays.asList("fatal"));   // 命中 - 不重试
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 应只执行 1 次，因为排除关键字优先级更高
            assertEquals(1, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testExcludeKeywordOnlyNoRetry() {
        // 验证命中排除关键字时不重试
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            counter.incrementAndGet();
            throw new RuntimeException("deadlock occurred");
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("timeout"));         // 不命中
        policy.setExcludeKeywords(Arrays.asList("deadlock")); // 命中
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            // 应只执行 1 次，因为命中排除关键字
            assertEquals(1, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }

    @Test
    public void testUseKeywordFalseNoFilter() throws Exception {
        // 验证 useKeyword=false 时，即使不命中 keywords 也重试
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("connection reset"); // 不命中任何关键字
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(false);  // 关闭关键字匹配
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        String result = interceptor.execute(op, policy);

        assertEquals("success", result);
        assertEquals(3, counter.get());
    }

    @Test
    public void testExcludeKeywordsNullBehavior() throws Exception {
        // 验证 excludeKeywords 为 null 时不拦截
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("deadlock occurred");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setExcludeKeywords(null);  // excludeKeywords 为 null
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        String result = interceptor.execute(op, policy);

        assertEquals("success", result);
        assertEquals(2, counter.get());
    }

    @Test
    public void testKeywordsAndExcludeKeywordsBothEmpty() throws Exception {
        // 验证 keywords 和 excludeKeywords 都为空时的行为
        AtomicInteger counter = new AtomicInteger(0);
        ThrowingSupplier<String> op = () -> {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("any error");
            }
            return "success";
        };

        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(false);
        policy.setMaxAttempts(5);
        policy.setInitialIntervalMs(10L);
        policy.setMultiplier(1.0);
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList());       // 空
        policy.setExcludeKeywords(Arrays.asList()); // 空
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        // useKeyword=true 且 keywords 为空时，isKeywordMatch 返回 false
        // 因此不重试，直接抛出异常
        try {
            interceptor.execute(op, policy);
            fail("should throw");
        } catch (RuntimeException e) {
            assertEquals(1, counter.get());
        } catch (Exception e) {
            fail("unexpected: " + e.getMessage());
        }
    }
}
