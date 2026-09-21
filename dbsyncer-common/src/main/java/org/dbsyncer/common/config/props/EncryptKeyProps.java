package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.dbsyncer.common.util.StringUtil;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 加密密钥配置（A 类：环境敏感，只读展示）。
 * <p>
 * 密钥来源优先级：
 * <ol>
 *   <li>系统属性 {@link ConfigKeys#SERVER_ENCRYPT_KEY}</li>
 *   <li>环境变量 {@code DBSYNCER_ENCRYPT_KEY}</li>
 *   <li>缺省值（仅开发环境）</li>
 * </ol>
 * <p>
 * 注意：密钥本身不在 UI 展示，只展示来源。
 */
public class EncryptKeyProps implements AppProps {

    /** 环境变量名 */
    private static final String ENV_KEY = "DBSYNCER_ENCRYPT_KEY";

    /**
     * 缺省加密密钥（仅开发环境使用）。
     * <p>
     * 生产环境务必通过系统属性或环境变量覆盖！
     */
    private static final String DEFAULT_KEY = "dbsyncer-dev-default-key-2024";

    /** 密钥来源：系统属性 / 环境变量 / 缺省值 */
    private String keySource = "缺省值（开发环境）";

    /** 密钥值（内部使用，不在 UI 展示） */
    private String keyValue = DEFAULT_KEY;

    @Override
    public void loadFromEnvironment(Environment env) {
        // 1. 优先系统属性
        String key = System.getProperty(ConfigKeys.SERVER_ENCRYPT_KEY);
        if (StringUtil.isNotBlank(key)) {
            this.keySource = "系统属性";
            this.keyValue = key;
            return;
        }

        // 2. 其次环境变量
        key = System.getenv(ENV_KEY);
        if (StringUtil.isNotBlank(key)) {
            this.keySource = "环境变量";
            this.keyValue = key;
            return;
        }

        // 3. 缺省值
        this.keySource = "缺省值（开发环境）";
        this.keyValue = DEFAULT_KEY;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        // 只存储来源，不存储密钥值（密钥从环境读取）
        entries.add(new ConfigEntry(ConfigKeys.SERVER_ENCRYPT_KEY_SOURCE, keySource, false));
        return entries;
    }

    /**
     * 获取密钥来源描述（用于 UI 展示）。
     */
    public String getKeySource() {
        return keySource;
    }

    /**
     * 获取密钥值（内部使用，不在 UI 展示）。
     */
    public String getKeyValue() {
        return keyValue;
    }

    /**
     * 设置密钥来源（用于 DB 加载）。
     */
    public void setKeySource(String keySource) {
        this.keySource = keySource;
    }
}
