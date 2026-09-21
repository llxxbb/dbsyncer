package org.dbsyncer.common.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.config.props.ConfigEntry;
import org.dbsyncer.common.util.JsonUtil;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Props 统一契约：自加载 + 自描述 + 敏感字段声明。
 * <p>
 * 顶层 Props 实现此接口，复合 Props（如 {@code ExecutorProps}、{@code BufferProps}）
 * 在内部完成子对象的创建与环境加载，子对象作为纯数据容器无需实现此接口。
 * <ul>
 *   <li>{@link #loadFromEnvironment} — 从 Spring Environment 读取配置值并填充自身字段</li>
 *   <li>{@link #toConfigEntries} — 返回自身的配置条目列表，用于启动期配置对照表打印</li>
 *   <li>{@link #getSensitiveFields} — 声明敏感字段，用于存储加密/解密和展示掩码</li>
 *   <li>{@link #forDisplay} — 返回用于展示的安全副本（敏感字段已掩码）</li>
 * </ul>
 */
public interface AppProps {

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
     *   <li>存储加密：序列化前对 secret 型字段加密</li>
     *   <li>存储解密：反序列化后对 secret 型字段解密</li>
     *   <li>展示掩码：forDisplay() 对所有声明字段执行掩码</li>
     *   <li>保存保护：前端传回掩码值时保留原值</li>
     * </ul>
     * <p>
     * 嵌套 Props 应递归收集子级的敏感字段。
     *
     * @return 敏感字段列表，不为 null
     */
    @JsonIgnore
    default List<SensitiveField> getSensitiveFields() {
        return Collections.emptyList();
    }

    /**
     * 返回用于展示的安全副本（敏感字段已掩码）。
     * <p>
     * 默认实现：JSON 深拷贝 → 遍历 {@link #getSensitiveFields()} → 逐字段掩码。
     * 各 Props 无需手写 forDisplay()，只要正确声明 getSensitiveFields() 即可自动获得。
     * <p>
     * 无敏感字段时返回 this（避免无谓的序列化开销）。
     * <p>
     * <b>性能说明</b>：有敏感字段时每次调用都会执行 JSON 序列化/反序列化。
     * 当前仅用于系统配置页展示（低频），性能可接受。
     * 若未来用于高频场景，需在调用方加缓存。
     *
     * @return 掩码后的副本，或 this（如果没有敏感字段）
     */
    @JsonIgnore
    default AppProps forDisplay() {
        List<SensitiveField> fields = getSensitiveFields();
        if (fields.isEmpty()) {
            return this;
        }
        // JSON 深拷贝：副本的 getSensitiveFields() 会创建新的 SensitiveField 实例指向副本字段
        String json = JsonUtil.objToJson(this);
        AppProps copy = JsonUtil.jsonToObj(json, this.getClass());
        // 对副本的敏感字段执行掩码
        for (SensitiveField field : copy.getSensitiveFields()) {
            field.mask();
        }
        return copy;
    }

    /**
     * 校验配置值的合法性（范围、非空等）。
     * <p>
     * 各 Props 子类覆写此方法实现自身字段的校验逻辑。
     * 复合 Props（如 {@code ExecutorProps}、{@code BufferProps}）应递归校验子级。
     *
     * @return 错误信息列表，为空表示校验通过，不为 null
     */
    default List<String> validate() {
        return Collections.emptyList();
    }

    /**
     * 恢复 incoming 中未修改的敏感字段：从 existing 中按字段名匹配，回填原值。
     * <p>
     * 通用机制，适用于任何 {@link AppProps}：
     * <ul>
     *   <li>系统级 Tab 保存（editTab）</li>
     *   <li>分组级配置保存（saveGroupNotifyConfig）</li>
     * </ul>
     * <p>
     * "未修改"的判定由 {@link SensitiveField#isUnchanged()} 负责。
     *
     * @param incoming 前端传入的 Props（可能包含掩码值/空值）
     * @param existing 数据库中的现有 Props（明文）
     */
    static void restoreMaskedFields(AppProps incoming, AppProps existing) {
        if (incoming == null || existing == null) {
            return;
        }
        Map<String, SensitiveField> exMap = new HashMap<>();
        for (SensitiveField f : existing.getSensitiveFields()) {
            exMap.put(f.getName(), f);
        }
        for (SensitiveField inField : incoming.getSensitiveFields()) {
            if (inField.isUnchanged()) {
                SensitiveField exField = exMap.get(inField.getName());
                if (exField != null && exField.getValue() != null) {
                    inField.setValue(exField.getValue());
                }
            }
        }
    }
}
