package org.dbsyncer.common.config;

import org.springframework.core.env.Environment;

/**
 * 可从 Spring Environment 加载配置的 Props 统一契约。
 * <p>
 * 实现类在 {@link #loadFromEnvironment} 内部完成类型转换与默认值处理，
 * 调用方只需传入 Environment，无需关心具体 key。
 * <p>
 * 仅顶层 Props（直接挂载到 SystemConfig 上的配置块）实现此接口；
 * 嵌套 Props（如 PoolProps、BufferActuatorProps）由父 Props 负责加载。
 */
public interface EnvironmentLoadable {

    /**
     * 从 Environment 读取配置值并填充自身字段。
     *
     * @param env Spring Environment，不为 null
     */
    void loadFromEnvironment(Environment env);
}
