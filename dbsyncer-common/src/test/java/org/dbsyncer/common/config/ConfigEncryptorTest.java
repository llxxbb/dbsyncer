package org.dbsyncer.common.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ConfigEncryptor 单元测试。
 */
public class ConfigEncryptorTest {

    @Test
    public void testEncryptDecrypt() {
        // 通过反射创建测试实例
        ConfigEncryptor encryptor = createTestEncryptor("test-secret-key-123");
        assertNotNull(encryptor);

        String plaintext = "my-secret-password";
        String encrypted = encryptor.encrypt(plaintext);

        // 加密后应有 ENC: 前缀
        assertTrue(encrypted.startsWith("ENC:"));
        // 加密后不等于明文
        assertNotEquals(plaintext, encrypted);

        // 解密应还原
        String decrypted = encryptor.tryDecrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    public void testTryDecryptPlaintext() {
        ConfigEncryptor encryptor = createTestEncryptor("test-secret-key-123");

        // 明文旧数据应直接返回
        String plaintext = "old-plaintext-password";
        String result = encryptor.tryDecrypt(plaintext);
        assertEquals(plaintext, result);
    }

    @Test
    public void testEncryptEmptyAndNull() {
        ConfigEncryptor encryptor = createTestEncryptor("test-secret-key-123");

        assertNull(encryptor.encrypt(null));
        assertEquals("", encryptor.encrypt(""));
        assertNull(encryptor.tryDecrypt(null));
        assertEquals("", encryptor.tryDecrypt(""));
    }

    @Test
    public void testDifferentKeysProduceDifferentCiphertexts() {
        ConfigEncryptor enc1 = createTestEncryptor("key-1");
        ConfigEncryptor enc2 = createTestEncryptor("key-2");

        String plaintext = "same-password";
        String encrypted1 = enc1.encrypt(plaintext);
        String encrypted2 = enc2.encrypt(plaintext);

        // 不同密钥加密结果不同
        assertNotEquals(encrypted1, encrypted2);

        // 各自能解密
        assertEquals(plaintext, enc1.tryDecrypt(encrypted1));
        assertEquals(plaintext, enc2.tryDecrypt(encrypted2));

        // 交叉解密失败（应返回原值）
        String crossDecrypted = enc2.tryDecrypt(encrypted1);
        assertNotEquals(plaintext, crossDecrypted);
    }

    @Test
    public void testKeySource() {
        ConfigEncryptor encryptor = createTestEncryptor("test-key");
        assertEquals("测试", encryptor.getKeySource());
    }

    private ConfigEncryptor createTestEncryptor(String key) {
        try {
            java.lang.reflect.Constructor<ConfigEncryptor> ctor =
                ConfigEncryptor.class.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(key, "测试");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
