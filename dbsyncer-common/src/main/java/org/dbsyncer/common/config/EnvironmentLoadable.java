package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.ConfigEntry;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;

/**
 * Props 统一契约：自加载 + 自描述 + 敏感字段声明。
 * <p>
 * 所有 Props（含顶层和嵌套）均实现此接口：
 * <ul>
 *   <li>{@link #loadFromEnvironment} — 从 Spring Environment 读取配置值并填充自身字段</li>
 *   <li>{@link #toConfigEntries} — 返回自身的配置条目列表，用于启动期配置对照表打印</li>
 *   <li>{@link #getSensitiveFields} — 声明敏感字段，用于存储加密/解密和展示掩码</li>
 *   <li>{@link #forDisplay} — 返回用于展示的安全副本（敏感字段已掩码）</li>
 * </ul>
 *
 * @version 1.2.0
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

    /**
     * 声明敏感字段列表（如密码、密钥）。
     * <p>
     * 用于：
     * <ul>
     *   <li>存储加密：序列化前对敏感字段加密</li>
     *   <li>存储解密：反序列化后对敏感字段解密</li>
     *   <li>保存保护：前端传回掩码值时保留原值</li>
     * </ul>
     * <p>
     * 嵌套 Props 应递归收集子级的敏感字段。
     *
     * @return 敏感字段列表，不为 null
     */
    default List<SensitiveField> getSensitiveFields() {
        return Collections.emptyList();
    }

    /**
     * 返回用于展示的安全副本（敏感字段已掩码）。
     * <p>
     * 默认返回 this（不掩码）。需要掩码的 Props 应覆写此方法，
     * 返回深拷贝并将敏感字段替换为掩码值。
     *
     * @return 掩码后的副本，或 this（如果不需掩码）
     */
    default EnvironmentLoadable forDisplay() {
        return this;
    }

    /**
     * 反序列化后的后处理钩子。
     * <p>
     * 复合 Props（如 {@code ExecutorProps}、{@code BufferProps}）从 JSON 反序列化后，
     * 子对象的运行时注入字段（如 {@code PoolProps} 的 {@code *Key}）为 null。
     * 覆写此方法重新注入，确保 {@link #toConfigEntries()} 等方法正常工作。
     * <p>
     * 由 {@code ConfigRegistry} 在 merge 完成后统一调用。
     */
    default void afterDeserialize() {
        // 默认无操作
    }
}
