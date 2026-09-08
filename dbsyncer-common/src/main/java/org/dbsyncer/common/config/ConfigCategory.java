package org.dbsyncer.common.config;

/**
 * 配置分类（ADR-0029）。
 * <p>
 * 定义三类配置的语义：
 * <ul>
 *   <li>A — 环境只读：从 Spring Environment 读取，不落库，不可通过 UI 修改</li>
 *   <li>B — 需重启：修改后需重启应用才能生效</li>
 *   <li>C — 立即生效：修改后立即生效，通过 ConfigChangeListener 通知</li>
 * </ul>
 */
public enum ConfigCategory {

    A("A类", "环境只读配置（只读，不落库）"),
    B("B类", "需重启配置"),
    C("C类", "立即生效配置");

    private final String prefix;
    private final String description;

    ConfigCategory(String prefix, String description) {
        this.prefix = prefix;
        this.description = description;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDescription() {
        return description;
    }

    /** 是否只从 Environment 读取（不落库） */
    public boolean isEnvironmentOnly() {
        return this == A;
    }

    /** 是否需要重启才能生效 */
    public boolean isRestartRequired() {
        return this == B;
    }

    /** 是否修改后立即生效 */
    public boolean isRuntimeApply() {
        return this == C;
    }
}
