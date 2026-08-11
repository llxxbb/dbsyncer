package org.dbsyncer.common.retry;

import org.dbsyncer.common.util.MatchMode;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class KeywordMatcherTest {

    @Test
    public void testExactMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.EXACT);
        assertEquals("deadlock", matcher.findMatch("deadlock"));
        assertEquals("timeout", matcher.findMatch("timeout"));
        assertNull(matcher.findMatch("connection timeout"));
        assertNull(matcher.findMatch("DEADLOCK"));
    }

    @Test
    public void testContainsMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CONTAINS);
        assertEquals("deadlock", matcher.findMatch("a deadlock error"));
        assertEquals("timeout", matcher.findMatch("request timeout occurred"));
        assertNull(matcher.findMatch("connection reset"));
    }

    @Test
    public void testCaseInsensitiveMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CASE_INSENSITIVE);
        assertEquals("deadlock", matcher.findMatch("DEADLOCK"));
        assertEquals("timeout", matcher.findMatch("Timeout"));
        assertNull(matcher.findMatch("deadlocks"));
    }

    @Test
    public void testContainsIgnoreCaseMatch() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock", "timeout"), MatchMode.CONTAINS_IGNORE_CASE);
        assertEquals("deadlock", matcher.findMatch("A DeadLock Occurred"));
        assertEquals("timeout", matcher.findMatch("TIMEOUT ERROR"));
        assertNull(matcher.findMatch("connection reset"));
    }

    @Test
    public void testNullMessage() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("deadlock"), MatchMode.CONTAINS);
        assertNull(matcher.findMatch(null));
    }

    @Test
    public void testEmptyKeywords() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList(), MatchMode.CONTAINS);
        assertNull(matcher.findMatch("deadlock"));
    }

    @Test
    public void testSpecialCharacters() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("Deadlock found"), MatchMode.CONTAINS);
        assertEquals("Deadlock found", matcher.findMatch("Deadlock found when trying to get lock"));
    }

    @Test
    public void testReturnsFirstMatchedKeyword() {
        KeywordMatcher matcher = new KeywordMatcher(Arrays.asList("error", "deadlock"), MatchMode.CONTAINS);
        assertEquals("error", matcher.findMatch("a deadlock error occurred"));
    }
}
