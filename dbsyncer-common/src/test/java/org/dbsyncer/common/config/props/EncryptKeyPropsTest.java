package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * EncryptKeyProps 单元测试。
 */
public class EncryptKeyPropsTest {

    @Test
    public void testLoadFromEnvironmentDefault() {
        // 清除系统属性以确保使用缺省值
        String originalValue = System.getProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
        try {
            System.clearProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
            
            EncryptKeyProps props = new EncryptKeyProps();
            // 直接调用 loadFromEnvironment(null)，因为缺省值不依赖 Environment
            props.loadFromEnvironment(null);

            assertEquals("缺省值（开发环境）", props.getKeySource());
            assertNotNull(props.getKeyValue());
        } finally {
            if (originalValue != null) {
                System.setProperty(ConfigKeys.SERVER_ENCRYPT_KEY, originalValue);
            }
        }
    }

    @Test
    public void testLoadFromEnvironmentSystemProperty() {
        String originalValue = System.getProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
        try {
            System.setProperty(ConfigKeys.SERVER_ENCRYPT_KEY, "my-system-property-key");
            
            EncryptKeyProps props = new EncryptKeyProps();
            props.loadFromEnvironment(null);

            assertEquals("系统属性", props.getKeySource());
            assertEquals("my-system-property-key", props.getKeyValue());
        } finally {
            if (originalValue != null) {
                System.setProperty(ConfigKeys.SERVER_ENCRYPT_KEY, originalValue);
            } else {
                System.clearProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
            }
        }
    }

    @Test
    public void testToConfigEntries() {
        String originalValue = System.getProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
        try {
            System.clearProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
            
            EncryptKeyProps props = new EncryptKeyProps();
            props.loadFromEnvironment(null);

            java.util.List<ConfigEntry> entries = props.toConfigEntries();
            assertEquals(1, entries.size());
            assertEquals(ConfigKeys.SERVER_ENCRYPT_KEY_SOURCE, entries.get(0).getKey());
            assertEquals("缺省值（开发环境）", entries.get(0).getValue());
        } finally {
            if (originalValue != null) {
                System.setProperty(ConfigKeys.SERVER_ENCRYPT_KEY, originalValue);
            }
        }
    }
}
