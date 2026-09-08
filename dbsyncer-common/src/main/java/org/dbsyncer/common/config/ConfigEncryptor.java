package org.dbsyncer.common.config;

import org.dbsyncer.common.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 敏感配置加解密工具。
 * <p>
 * 密钥来源优先级：
 * <ol>
 *   <li>系统属性 {@link ConfigKeys#SERVER_ENCRYPT_KEY}</li>
 *   <li>环境变量 {@code DBSYNCER_ENCRYPT_KEY}</li>
 *   <li>缺省值（仅开发环境，生产环境务必覆盖）</li>
 * </ol>
 * <p>
 * 加密算法：AES/GCM/NoPadding，128 位密钥，96 位 IV。
 * 密文格式：ENC:Base64(IV + ciphertext + tag)。
 */
public class ConfigEncryptor {

    private static final Logger logger = LoggerFactory.getLogger(ConfigEncryptor.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENV_KEY = "DBSYNCER_ENCRYPT_KEY";

    /**
     * 缺省加密密钥（仅开发环境使用）。
     * <p>
     * 生产环境务必通过系统属性或环境变量覆盖！
     */
    private static final String DEFAULT_KEY = "dbsyncer-dev-default-key-2024";

    /** 密文前缀标识，用于区分加密值和明文旧数据 */
    private static final String ENCRYPTED_PREFIX = "ENC:";

    private static volatile ConfigEncryptor instance;

    /** 密钥来源标记 */
    private final String keySource;
    private final byte[] keyBytes;

    private ConfigEncryptor(String key, String keySource) {
        this.keyBytes = deriveKey(key);
        this.keySource = keySource;
    }

    /**
     * 获取全局实例。
     */
    public static ConfigEncryptor getInstance() {
        if (instance == null) {
            synchronized (ConfigEncryptor.class) {
                if (instance == null) {
                    instance = createInstance();
                }
            }
        }
        return instance;
    }

    /**
     * 是否可用（始终可用，因为有缺省值）。
     */
    public boolean isAvailable() {
        return keyBytes != null;
    }

    /**
     * 获取密钥来源描述（脱敏）。
     */
    public String getKeySource() {
        return keySource;
    }

    /**
     * 加密明文。返回格式：ENC:Base64(IV + ciphertext + tag)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM), spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // IV + ciphertext 拼接
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return ENCRYPTED_PREFIX + java.util.Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            logger.error("加密失败: {}", e.getMessage());
            return plaintext;
        }
    }

    /**
     * 尝试解密。如果值不是加密格式（明文旧数据），返回原值。
     */
    public String tryDecrypt(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!value.startsWith(ENCRYPTED_PREFIX)) {
            // 明文旧数据，直接返回
            return value;
        }
        try {
            String encoded = value.substring(ENCRYPTED_PREFIX.length());
            byte[] decoded = java.util.Base64.getDecoder().decode(encoded);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, ALGORITHM), spec);
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            logger.warn("解密失败（可能是明文旧数据或密钥不匹配）: {}", e.getMessage());
            return value;
        }
    }

    private static ConfigEncryptor createInstance() {
        // 1. 优先系统属性
        String key = System.getProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
        if (StringUtil.isNotBlank(key)) {
            logger.info("敏感配置加密密钥来源: 系统属性 ({})", ConfigKeys.SERVER_ENCRYPT_KEY);
            return new ConfigEncryptor(key, "系统属性");
        }

        // 2. 其次环境变量
        key = System.getenv(ENV_KEY);
        if (StringUtil.isNotBlank(key)) {
            logger.info("敏感配置加密密钥来源: 环境变量 ({})", ENV_KEY);
            return new ConfigEncryptor(key, "环境变量");
        }

        // 3. 缺省值（仅开发环境）
        logger.warn("未配置加密密钥，使用缺省值（仅适用于开发环境）。生产环境请通过 -D{}=<密钥> 或环境变量 {} 配置！",
                ConfigKeys.SERVER_ENCRYPT_KEY, ENV_KEY);
        return new ConfigEncryptor(DEFAULT_KEY, "缺省值（开发环境）");
    }

    /**
     * 从任意长度密钥字符串派生 128 位 AES 密钥。
     */
    private static byte[] deriveKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes("UTF-8"));
            // 取前 16 字节作为 AES-128 密钥
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);
            return keyBytes;
        } catch (Exception e) {
            logger.error("密钥派生失败: {}", e.getMessage());
            return null;
        }
    }
}
