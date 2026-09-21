package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.dbsyncer.common.config.SensitiveField;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A 类：存储连接配置（只读，不落库）
 */

public class StorageConnectionProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    private String type;
    private String mysqlUrl;
    private String mysqlUsername;
    private String mysqlPassword;
    private String mysqlDriverClassName;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMysqlUrl() { return mysqlUrl; }
    public void setMysqlUrl(String mysqlUrl) { this.mysqlUrl = mysqlUrl; }
    public String getMysqlUsername() { return mysqlUsername; }
    public void setMysqlUsername(String mysqlUsername) { this.mysqlUsername = mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public void setMysqlPassword(String mysqlPassword) { this.mysqlPassword = mysqlPassword; }
    public String getMysqlDriverClassName() { return mysqlDriverClassName; }
    public void setMysqlDriverClassName(String mysqlDriverClassName) { this.mysqlDriverClassName = mysqlDriverClassName; }

    public void loadFromEnvironment(Environment env) {
        this.type = env.getProperty(ConfigKeys.STORAGE_TYPE, String.class, type);
        this.mysqlUrl = env.getProperty(ConfigKeys.STORAGE_MYSQL_URL, String.class, mysqlUrl);
        this.mysqlUsername = env.getProperty(ConfigKeys.STORAGE_MYSQL_USERNAME, String.class, mysqlUsername);
        this.mysqlPassword = env.getProperty(ConfigKeys.STORAGE_MYSQL_PASSWORD, String.class, mysqlPassword);
        this.mysqlDriverClassName = env.getProperty(ConfigKeys.STORAGE_MYSQL_DRIVER_CLASS_NAME, String.class, mysqlDriverClassName);
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (type != null && "mysql".equalsIgnoreCase(type)) {
            if (mysqlUrl == null || mysqlUrl.trim().isEmpty()) {
                errors.add("MySQL 存储 URL 不能为空");
            }
            if (mysqlUsername == null || mysqlUsername.trim().isEmpty()) {
                errors.add("MySQL 存储用户名不能为空");
            }
        }
        return errors;
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.STORAGE_TYPE, type));
        entries.add(new ConfigEntry(ConfigKeys.STORAGE_MYSQL_URL, mysqlUrl));
        entries.add(new ConfigEntry(ConfigKeys.STORAGE_MYSQL_USERNAME, mysqlUsername));
        entries.add(new ConfigEntry(ConfigKeys.STORAGE_MYSQL_PASSWORD, mysqlPassword, true));
        entries.add(new ConfigEntry(ConfigKeys.STORAGE_MYSQL_DRIVER_CLASS_NAME, mysqlDriverClassName));
        return entries;
    }

    /**
     * 声明敏感字段：
     * <ul>
     *   <li>mysqlUrl / mysqlUsername：display-mask 型，不加密，展示时部分遮盖</li>
     *   <li>mysqlPassword：secret 型，加密存储，展示时全掩码</li>
     * </ul>
     */
    @Override
    @JsonIgnore
    public List<SensitiveField> getSensitiveFields() {
        List<SensitiveField> fields = new ArrayList<>();
        fields.add(SensitiveField.displayMask("storageConn.mysqlUrl",
                () -> mysqlUrl, v -> this.mysqlUrl = v, SensitiveField::maskIp));
        fields.add(SensitiveField.displayMask("storageConn.mysqlUsername",
                () -> mysqlUsername, v -> this.mysqlUsername = v, SensitiveField::maskUsername));
        fields.add(SensitiveField.secret("storageConn.mysqlPassword",
                () -> mysqlPassword, v -> this.mysqlPassword = v));
        return fields;
    }
}
