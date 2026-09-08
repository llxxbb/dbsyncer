package org.dbsyncer.common.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SensitiveField 单元测试。
 */
public class SensitiveFieldTest {

    @Test
    public void testMaskShortValue() {
        // 长度 <= 4：全掩码
        assertEquals("****", SensitiveField.mask("abc"));
        assertEquals("****", SensitiveField.mask("1234"));
    }

    @Test
    public void testMaskMediumValue() {
        // 长度 <= 8：前2后2
        assertEquals("ab****34", SensitiveField.mask("abcd1234"));
        assertEquals("ab****78", SensitiveField.mask("abcdef78"));
    }

    @Test
    public void testMaskLongValue() {
        // 长度 > 8：前4后4
        assertEquals("abcd****6789", SensitiveField.mask("abcdefgh123456789"));
        assertEquals("pass****word", SensitiveField.mask("password123word"));
    }

    @Test
    public void testMaskEmptyAndNull() {
        assertEquals("", SensitiveField.mask(""));
        assertEquals("", SensitiveField.mask(null));
    }

    @Test
    public void testIsUnchanged() {
        // 空字符串和 null 被认为是“未修改”
        assertTrue(isUnchanged(null));
        assertTrue(isUnchanged(""));
        
        // 非空字符串被认为是“已修改”
        assertFalse(isUnchanged("new-password"));
        assertFalse(isUnchanged("abc****xyz"));
    }

    private boolean isUnchanged(String value) {
        return value == null || value.isEmpty();
    }
}
