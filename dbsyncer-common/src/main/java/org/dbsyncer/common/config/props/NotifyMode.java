package org.dbsyncer.common.config.props;

/**
 * 通知配置模式（用于分组级别）。
 * <ul>
 *   <li>DISABLED：禁用通知，该分组不发任何通知</li>
 *   <li>INHERIT：继承全局配置（缺省）</li>
 *   <li>OVERRIDE：使用分组自己的配置</li>
 * </ul>
 */
public enum NotifyMode {

    /**
     * 禁用通知，该分组不发任何通知
     */
    DISABLED,

    /**
     * 继承全局配置（缺省值）
     */
    INHERIT,

    /**
     * 使用分组自己的配置
     */
    OVERRIDE;

    /**
     * 安全解析，无效值返回缺省 INHERIT。
     */
    public static NotifyMode parse(String value) {
        if (value == null || value.isEmpty()) {
            return INHERIT;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INHERIT;
        }
    }
}
