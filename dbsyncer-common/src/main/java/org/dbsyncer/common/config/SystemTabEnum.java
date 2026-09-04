package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.*;

/**
 * Tab 元数据单一来源。
 * <p>
 * 每个枚举项对应 SystemConfig 中的一个子对象 Tab，
 * 驱动 UI 渲染、校验、提示与 sidecar 键生成。
 */
public enum SystemTabEnum {

    // 基础 + B 类
    BASIC("basic", BasicProps.class, false, false),
    EXECUTORS("executors", ExecutorProps.class, true, true),
    BUFFERS("buffers", BufferProps.class, true, true),

    // C 类（立即生效）
    RETRY("retry", RetryProps.class, false, true),
    NOTIFY("notify", NotifyProps.class, false, true),
    ERROR("error", ErrorProps.class, false, true),
    RUNTIME("runtime", RuntimeProps.class, false, true),
    TEMPLATE("template", TemplateProps.class, false, true);

    private final String key;
    private final Class<?> propsType;
    private final boolean restartRequired;
    private final boolean runtimeApply;

    SystemTabEnum(String key, Class<?> propsType, boolean restartRequired, boolean runtimeApply) {
        this.key = key;
        this.propsType = propsType;
        this.restartRequired = restartRequired;
        this.runtimeApply = runtimeApply;
    }

    public String getKey() {
        return key;
    }

    public Class<?> getPropsType() {
        return propsType;
    }

    /** 是否需要重启应用才能生效 */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    /** 是否在保存后需要运行期回写（C 类立即生效） */
    public boolean isRuntimeApply() {
        return runtimeApply;
    }

    /**
     * 从 key 查找枚举，找不到返回 null
     */
    public static SystemTabEnum of(String key) {
        if (key == null) {
            return null;
        }
        for (SystemTabEnum tab : values()) {
            if (tab.key.equals(key)) {
                return tab;
            }
        }
        return null;
    }
}
