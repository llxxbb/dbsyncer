package org.dbsyncer.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 配置对象深拷贝。ADR-0029 实施修订 R1（D5）。
 * <p>
 * <b>为什么不用 {@code JsonUtil}</b>：它全局启用 {@code Include.NON_DEFAULT}，
 * "值等于 Java 类型默认值"（{@code false} / {@code 0} / {@code ""} / {@code null}）的属性会被整个省略。
 * 用它做 {@code jsonToObj(objToJson(x))} 往返拷贝时，
 * 初始值不等于类型默认的字段会被抹回初始值（例：{@code enableStorageWriteFail=false} 拷完变 {@code true}），
 * 连一次纯内存拷贝都能吃掉用户已生效的值；另外 A 类子对象带 {@code @JsonIgnore}，
 * 往返会把 {@code server} / {@code worker} 整个拷没。
 * <p>
 * 本类自带不启用任何 inclusion 过滤的 mapper，只做无损深拷贝，
 * 顺带切断 propsConfig / dbConfig 与 mergedConfig 之间的子对象共享引用（m1）。
 * 不改动 {@code JsonUtil}，其余 ConfigModel 的序列化行为不受影响。
 */
public final class ConfigCopy {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ConfigCopy() {
    }

    /**
     * 深拷贝配置子对象。
     *
     * @param src  源对象，可为 null
     * @param type 目标类型（src 或其父类均可）
     * @param <T>  源对象类型
     * @return 新实例，src 为 null 时返回 null
     */
    public static <T> T copy(T src, Class<? extends T> type) {
        if (src == null) {
            return null;
        }
        try {
            return MAPPER.treeToValue(MAPPER.valueToTree(src), type);
        } catch (Exception e) {
            throw new RuntimeException("配置深拷贝失败: " + type.getName() + ", " + e.getMessage(), e);
        }
    }
}
