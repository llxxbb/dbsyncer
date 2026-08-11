package org.dbsyncer.common.retry;

import org.dbsyncer.common.util.MatchMode;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class KeywordMatcherTest {

    @Test
    public void testExactMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.EXACT);
        assertTrue(matcher.matches("deadlock"));
        assertTrue(matcher.matches("timeout"));
        assertFalse(matcher.matches("connection timeout"));
        assertFalse(matcher.matches("DEADLOCK"));
    }

    @Test
    public void testContainsMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CONTAINS);
        assertTrue(matcher.matches("a deadlock error"));
        assertTrue(matcher.matches("request timeout occurred"));
        assertFalse(matcher.matches("connection reset"));
    }

    @Test
    public void TestCaseInsensitiveMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CASE_INSENSITIVE);
        assertTrue(matcher.matches("DEADLOCK"));
        assertTrue(matcher.matches("Timeout"));
        assertFalse(matcher.matches("deadlocks"));
    }

    @Test
    public void testContainsIgnoreCaseMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CONTAINS_IGNORE_CASE);
        assertTrue(matcher.matches("A DeadLock Occurred"));
        assertTrue(matcher.matches("TIMEOUT ERROR"));
        assertFalse(matcher.matches("connection reset"));
    }

    @Test
    public void testNullMessage() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock"), MatchMode.CONTAINS);
        assertFalse(matcher.matches(null));
    }

    @Test
    public void testEmptyKeywords() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList(), MatchMode.CONTAINS);
        assertFalse(matcher.matches("deadlock"));
    }

    @Test
    public void testSpecialCharacters() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("Deadlock found"), MatchMode.CONTAINS);
        assertTrue(matcher.matches("Deadlock found when trying to get lock"));
    }
}
