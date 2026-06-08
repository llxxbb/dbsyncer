package org.dbsyncer.common.retry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class RetryPolicyTest {

    @Test
    public void testDisable() {
        RetryPolicy policy = new RetryPolicy();
        policy.setDisable(true);
        assertTrue(policy.isDisable());
    }

    @Test
    public void testKeywordMatchExact() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock", "timeout"));
        policy.setMatchMode(RetryPolicy.MatchMode.EXACT);

        assertTrue(policy.isKeywordMatch("deadlock"));
        assertTrue(policy.isKeywordMatch("timeout"));
        assertFalse(policy.isKeywordMatch("connection timeout"));
        assertFalse(policy.isKeywordMatch("DEADLOCK"));
    }

    @Test
    public void testKeywordMatchContains() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock", "timeout"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        assertTrue(policy.isKeywordMatch("a deadlock occurred"));
        assertTrue(policy.isKeywordMatch("request timeout"));
        assertFalse(policy.isKeywordMatch("connection reset"));
    }

    @Test
    public void testKeywordMatchCaseInsensitive() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock", "timeout"));
        policy.setMatchMode(RetryPolicy.MatchMode.CASE_INSENSITIVE);

        assertTrue(policy.isKeywordMatch("DEADLOCK"));
        assertTrue(policy.isKeywordMatch("Timeout"));
        assertFalse(policy.isKeywordMatch("deadlocks"));
    }

    @Test
    public void testKeywordMatchContainsIgnoreCase() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock", "timeout"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS_IGNORE_CASE);

        assertTrue(policy.isKeywordMatch("A DeadLock Occurred"));
        assertTrue(policy.isKeywordMatch("TIMEOUT error"));
        assertFalse(policy.isKeywordMatch("connection reset"));
    }

    @Test
    public void testKeywordMatchUseKeywordFalse() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(false);
        // should always return true when useKeyword is false
        assertTrue(policy.isKeywordMatch("anything"));
    }

    @Test
    public void testKeywordMatchEmptyMessage() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        assertFalse(policy.isKeywordMatch(""));
        assertFalse(policy.isKeywordMatch(null));
    }

    @Test
    public void testCalculateInterval() {
        RetryPolicy policy = new RetryPolicy();
        policy.setInitialIntervalMs(100L);
        policy.setMultiplier(2.0);
        policy.setMaxIntervalMs(1000L);

        assertEquals(100L, policy.calculateInterval(0));
        assertEquals(200L, policy.calculateInterval(1));
        assertEquals(400L, policy.calculateInterval(2));
        assertEquals(800L, policy.calculateInterval(3));
        assertEquals(1000L, policy.calculateInterval(4)); // capped
        assertEquals(1000L, policy.calculateInterval(10)); // capped
    }

    // ==================== isExcludeKeywordMatch 测试 ====================

    @Test
    public void testIsExcludeKeywordMatchExact() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList("illegal", "critical"));
        policy.setMatchMode(RetryPolicy.MatchMode.EXACT);

        assertTrue(policy.isExcludeKeywordMatch("illegal"));
        assertTrue(policy.isExcludeKeywordMatch("critical"));
        assertFalse(policy.isExcludeKeywordMatch("an illegal operation"));
        assertFalse(policy.isExcludeKeywordMatch("ILLEGAL"));
    }

    @Test
    public void testIsExcludeKeywordMatchContainsIgnoreCase() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList("deadlock", "oom"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS_IGNORE_CASE);

        assertTrue(policy.isExcludeKeywordMatch("a deadlock occurred"));
        assertTrue(policy.isExcludeKeywordMatch("DEADLOCK in thread"));
        assertTrue(policy.isExcludeKeywordMatch("java.lang.OutOfMemoryError: OOM"));
        assertFalse(policy.isExcludeKeywordMatch("connection timeout"));
    }

    @Test
    public void testIsExcludeKeywordMatchNullMessage() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList("deadlock"));
        assertFalse(policy.isExcludeKeywordMatch(null));
    }

    @Test
    public void testIsExcludeKeywordMatchEmptyExcludeKeywords() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList());
        assertFalse(policy.isExcludeKeywordMatch("deadlock"));
    }

    @Test
    public void testIsExcludeKeywordMatchNullExcludeKeywords() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(null);
        assertFalse(policy.isExcludeKeywordMatch("deadlock"));
    }

    // ==================== 缓存逻辑测试 ====================

    @Test
    public void testGetMatcherCacheInitialization() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(Arrays.asList("deadlock", "timeout"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS_IGNORE_CASE);

        // 第一次调用应初始化 matcher
        org.dbsyncer.common.retry.KeywordMatcher matcher1 = policy.getMatcher();
        assertNotNull(matcher1);

        // 第二次调用应返回同一个实例
        org.dbsyncer.common.retry.KeywordMatcher matcher2 = policy.getMatcher();
        assertSame(matcher1, matcher2);
    }

    @Test
    public void testGetExcludeMatcherCacheInitialization() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList("fatal", "critical"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS_IGNORE_CASE);

        // 第一次调用应初始化 excludeMatcher
        org.dbsyncer.common.retry.KeywordMatcher matcher1 = policy.getExcludeMatcher();
        assertNotNull(matcher1);

        // 第二次调用应返回同一个实例
        org.dbsyncer.common.retry.KeywordMatcher matcher2 = policy.getExcludeMatcher();
        assertSame(matcher1, matcher2);
    }

    @Test
    public void testGetMatcherNullKeywordsReturnsMatcher() {
        RetryPolicy policy = new RetryPolicy();
        policy.setKeywords(null);

        // 即使 keywords 为 null，getMatcher 也应返回非 null
        org.dbsyncer.common.retry.KeywordMatcher matcher = policy.getMatcher();
        assertNotNull(matcher);

        // 匹配任何消息都应返回 false
        assertFalse(matcher.matches("anything"));
    }

    @Test
    public void testGetExcludeMatcherNullKeywordsReturnsMatcher() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(null);

        // 即使 excludeKeywords 为 null，getExcludeMatcher 也应返回非 null
        org.dbsyncer.common.retry.KeywordMatcher matcher = policy.getExcludeMatcher();
        assertNotNull(matcher);

        // 匹配任何消息都应返回 false
        assertFalse(matcher.matches("anything"));
    }

    // ==================== setKeywords/setExcludeKeywords 清空缓存测试 ====================

    @Test
    public void testSetKeywordsClearsMatcherCache() {
        RetryPolicy policy = new RetryPolicy();
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        // 获取初始 matcher
        org.dbsyncer.common.retry.KeywordMatcher matcher1 = policy.getMatcher();

        // 修改 keywords 应清空缓存
        policy.setKeywords(Arrays.asList("timeout"));
        org.dbsyncer.common.retry.KeywordMatcher matcher2 = policy.getMatcher();

        // 验证 matcher 已重建为新实例
        assertNotSame(matcher1, matcher2);

        // 验证新 matcher 能匹配 timeout 但不能匹配 deadlock
        assertTrue(matcher2.matches("a timeout occurred"));
        assertFalse(matcher2.matches("a deadlock occurred"));
    }

    @Test
    public void testSetExcludeKeywordsClearsExcludeMatcherCache() {
        RetryPolicy policy = new RetryPolicy();
        policy.setExcludeKeywords(Arrays.asList("fatal"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        // 获取初始 excludeMatcher
        org.dbsyncer.common.retry.KeywordMatcher matcher1 = policy.getExcludeMatcher();

        // 修改 excludeKeywords 应清空缓存
        policy.setExcludeKeywords(Arrays.asList("critical"));
        org.dbsyncer.common.retry.KeywordMatcher matcher2 = policy.getExcludeMatcher();

        // 验证 excludeMatcher 已重建为新实例
        assertNotSame(matcher1, matcher2);

        // 验证新 excludeMatcher 能匹配 critical 但不能匹配 fatal
        assertTrue(matcher2.matches("a critical error"));
        assertFalse(matcher2.matches("a fatal error"));
    }

    @Test
    public void testSetMatchModeClearsBothMatchers() {
        RetryPolicy policy = new RetryPolicy();
        policy.setKeywords(Arrays.asList("deadlock"));
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        // 获取初始 matchers
        org.dbsyncer.common.retry.KeywordMatcher matcher1 = policy.getMatcher();
        policy.setExcludeKeywords(Arrays.asList("fatal"));
        org.dbsyncer.common.retry.KeywordMatcher excludeMatcher1 = policy.getExcludeMatcher();

        // 修改 matchMode 应清空两个 matcher
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS_IGNORE_CASE);
        org.dbsyncer.common.retry.KeywordMatcher matcher2 = policy.getMatcher();
        org.dbsyncer.common.retry.KeywordMatcher excludeMatcher2 = policy.getExcludeMatcher();

        // 验证两个 matcher 都已重建为新实例
        assertNotSame(matcher1, matcher2);
        assertNotSame(excludeMatcher1, excludeMatcher2);
    }

    @Test
    public void testKeywordMatchNullMessageAndKeywords() {
        RetryPolicy policy = new RetryPolicy();
        policy.setUseKeyword(true);
        policy.setKeywords(null);
        policy.setMatchMode(RetryPolicy.MatchMode.CONTAINS);

        assertFalse(policy.isKeywordMatch(null));
        assertFalse(policy.isKeywordMatch(""));
    }
}
