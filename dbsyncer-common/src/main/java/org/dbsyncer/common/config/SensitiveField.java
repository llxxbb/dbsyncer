package org.dbsyncer.common.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 敏感字段描述。
 * <p>
 * 每个 Props 通过 {@link EnvironmentLoadable#getSensitiveFields()} 声明自己的敏感字段，
 * 系统在存储加密、展示掩码、保存保护等出口统一处理。
 * <p>
 * Props 只需声明一次，三个场景自动覆盖。
 */
public class SensitiveField {

    /** 统一掩码值（前端和 API 返回统一使用） */
    public static final String MASK = "********";

    private final String name;
    private final Supplier<String> getter;
    private final Consumer<String> setter;

    public SensitiveField(String name, Supplier<String> getter, Consumer<String> setter) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return getter.get();
    }

    public void setValue(String value) {
        setter.accept(value);
    }

    /**
     * 判断当前值是否为“未修改”状态（用于保存保护）。
     * <p>
     * 未修改状态：空字符串或 null（前端未输入新值）。
     */
    public boolean isUnchanged() {
        String val = getter.get();
        return val == null || val.isEmpty();
    }

    /**
     * 掩码：非空值替换为 MASK。
     */
    public void mask() {
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(MASK);
        }
    }

    /**
     * 加密：非空值通过 encryptor 加密。
     */
    public void encrypt(ConfigEncryptor encryptor) {
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(encryptor.encrypt(val));
        }
    }

    /**
     * 解密：非空值尝试解密，解密失败则保留原值（兼容明文旧数据）。
     */
    public void decrypt(ConfigEncryptor encryptor) {
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(encryptor.tryDecrypt(val));
        }
    }

    /**
     * 对字符串执行掩码（静态工具方法，供 forDisplay() 使用）。
     * <p>
     * 脱敏规则：
     * <ul>
     *   <li>长度 <= 4：全掩码 ****</li>
     *   <li>长度 <= 8：前2后2，中间 ****</li>
     *   <li>长度 > 8：前4后4，中间 ****</li>
     * </ul>
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int len = value.length();
        if (len <= 4) {
            return "****";
        } else if (len <= 8) {
            return value.substring(0, 2) + "****" + value.substring(len - 2);
        } else {
            return value.substring(0, 4) + "****" + value.substring(len - 4);
        }
    }
}
