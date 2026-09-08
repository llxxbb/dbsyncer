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
     * 判断当前值是否为掩码值（用于保存保护：前端传回掩码时保留原值）。
     */
    public boolean isMaskedValue() {
        return MASK.equals(getter.get());
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
     */
    public static String mask(String value) {
        return (value != null && !value.isEmpty()) ? MASK : "";
    }
}
