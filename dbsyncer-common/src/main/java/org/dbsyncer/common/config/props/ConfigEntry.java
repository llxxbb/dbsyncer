package org.dbsyncer.common.config.props;

import java.io.Serializable;

/**
 * 配置条目，用于启动期配置对照表打印。
 * <p>
 * 每个 Props 子类通过 toConfigEntries() 返回自己的配置条目列表，
 * 由 ConfigComparator 统一格式化输出。
 */
public class ConfigEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置键名（如 dbsyncer.parser.general.thread-core-size）
     */
    private final String key;

    /**
     * 配置值
     */
    private final Object value;

    /**
     * 是否敏感信息（如密码、webhook URL），需要脱敏显示
     */
    private final boolean sensitive;

    public ConfigEntry(String key, Object value) {
        this(key, value, false);
    }

    public ConfigEntry(String key, Object value, boolean sensitive) {
        this.key = key;
        this.value = value;
        this.sensitive = sensitive;
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    /**
     * 获取显示值，敏感信息返回 "***"
     */
    public String getDisplayValue() {
        if (value == null) {
            return "-";
        }
        if (sensitive) {
            String s = String.valueOf(value);
            return s.isEmpty() ? "-" : "***";
        }
        return String.valueOf(value);
    }
}
