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
}
