package org.dbsyncer.common.config;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感字段描述。
 * <p>
 * 每个 Props 通过 {@link EnvironmentLoadable#getSensitiveFields()} 声明自己的敏感字段，
 * 系统在存储加密、展示掩码、保存保护等出口统一处理。
 * <p>
 * 两种语义由字段自身决定：
 * <ul>
 *   <li><b>secret</b>：存储加密 + 全掩码展示（如密码、密钥）</li>
 *   <li><b>display-mask</b>：不加密 + 自定义掩码展示（如 URL、用户名）</li>
 * </ul>
 * Props 只需声明一次，各场景自动覆盖。
 */
public class SensitiveField {

    /** 统一全掩码值（前端和 API 返回统一使用） */
    public static final String MASK = "********";

    /** 全掩码函数 */
    private static final Function<String, String> FULL_MASK = v -> MASK;

    /** IP 脱敏正则（预编译） */
    private static final Pattern IP_PATTERN =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private final String name;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final Function<String, String> maskingFn;
    private final boolean encryptable;

    /**
     * 全功能构造。
     *
     * @param name        字段名（用于匹配恢复）
     * @param getter      取值
     * @param setter      赋值
     * @param maskingFn   掩码函数
     * @param encryptable 是否参与存储加密
     */
    public SensitiveField(String name, Supplier<String> getter, Consumer<String> setter,
                          Function<String, String> maskingFn, boolean encryptable) {
        this.name = name;
        this.getter = getter;
        this.setter = setter;
        this.maskingFn = maskingFn;
        this.encryptable = encryptable;
    }

    // ===== 工厂方法 =====

    /**
     * 创建 secret 型字段：存储加密 + 全掩码（********）。
     * <p>
     * 适用于密码、密钥、secret 等需要加密存储且展示时完全遮盖的字段。
     */
    public static SensitiveField secret(String name, Supplier<String> getter, Consumer<String> setter) {
        return new SensitiveField(name, getter, setter, FULL_MASK, true);
    }

    /**
     * 创建 display-mask 型字段：不加密 + 自定义掩码展示。
     * <p>
     * 适用于 URL、用户名等不需要加密存储但展示时需要部分遮盖的字段。
     *
     * @param maskingFn 掩码函数，如 {@link #maskIp(String)} 或 {@link #maskUsername(String)}
     */
    public static SensitiveField displayMask(String name, Supplier<String> getter,
                                             Consumer<String> setter,
                                             Function<String, String> maskingFn) {
        return new SensitiveField(name, getter, setter, maskingFn, false);
    }

    // ===== 实例方法 =====

    public String getName() {
        return name;
    }

    public String getValue() {
        return getter.get();
    }

    public void setValue(String value) {
        setter.accept(value);
    }

    /** 是否参与存储加密 */
    public boolean isEncryptable() {
        return encryptable;
    }

    /**
     * 判断当前值是否为"未修改"状态（用于保存保护）。
     * <p>
     * 未修改状态：空字符串或 null（前端未输入新值）。
     */
    public boolean isUnchanged() {
        String val = getter.get();
        return val == null || val.isEmpty();
    }

    /**
     * 掩码：非空值通过自身的 maskingFn 执行掩码。
     * <p>
     * secret 型 → 全掩码（********）<br>
     * display-mask 型 → 自定义掩码（由 maskingFn 决定）
     */
    public void mask() {
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(maskingFn.apply(val));
        }
    }

    /**
     * 加密：仅 secret 型字段生效，display-mask 型为 no-op。
     */
    public void encrypt(ConfigEncryptor encryptor) {
        if (!encryptable) {
            return;
        }
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(encryptor.encrypt(val));
        }
    }

    /**
     * 解密：仅 secret 型字段生效，display-mask 型为 no-op。
     * <p>
     * 解密失败时保留原值（兼容明文旧数据）。
     */
    public void decrypt(ConfigEncryptor encryptor) {
        if (!encryptable) {
            return;
        }
        String val = getter.get();
        if (val != null && !val.isEmpty()) {
            setter.accept(encryptor.tryDecrypt(val));
        }
    }

    // ===== 静态掩码工具方法 =====

    /**
     * 通用部分掩码（供 display-mask 字段的 maskingFn 使用）。
     * <p>
     * 脱敏规则：
     * <ul>
     *   <li>长度 &lt;= 4：全掩码 ****</li>
     *   <li>长度 &lt;= 8：前2后2，中间 ****</li>
     *   <li>长度 &gt; 8：前4后4，中间 ****</li>
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

    /**
     * IP 段脱敏：将 URL 中的 IP 地址替换为 ***.***.***.***。
     * <p>
     * 示例：{@code jdbc:mysql://192.168.1.100:3306/db} → {@code jdbc:mysql://***.***.***.***:3306/db}
     */
    public static String maskIp(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        Matcher m = IP_PATTERN.matcher(url);
        return m.replaceAll("***.***.***.***");
    }

    /**
     * 用户名脱敏。
     * <p>
     * 脱敏规则：
     * <ul>
     *   <li>长度 &lt;= 4：全掩码 ****</li>
     *   <li>长度 &gt; 4：前4 + ****</li>
     * </ul>
     */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "";
        }
        if (username.length() <= 4) {
            return "****";
        }
        return username.substring(0, 4) + "****";
    }
}
