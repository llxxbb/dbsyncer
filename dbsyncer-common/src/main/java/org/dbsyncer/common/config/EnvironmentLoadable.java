package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.ConfigEntry;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;

/**
 * Props 统一契约：自加载 + 自描述。
 * <p>
 * 所有 Props（含顶层和嵌套）均实现此接口：
 * <ul>
 *   <li>{@link #loadFromEnvironment} — 从 Spring Environment 读取配置值并填充自身字段</li>
 *   <li>{@link #toConfigEntries} — 返回自身的配置条目列表，用于启动期配置对照表打印</li>
 * </ul>
 *
 * @version 1.1.0
 * @Author AE86
 * @Date 2025-01-24
 */
public interface EnvironmentLoadable {

    /**
     * 从 Environment 读取配置值并填充自身字段。
     *
     * @param env Spring Environment，不为 null
     */
    void loadFromEnvironment(Environment env);

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     * <p>
     * 默认返回空列表；各 Props 子类按自身字段覆盖实现。
     *
     * @return 配置条目列表，不为 null
     */
    default List<ConfigEntry> toConfigEntries() {
        return Collections.emptyList();
    }
}
