package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.dbsyncer.common.config.SensitiveField;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 邮件通知子配置。
 * <p>
 * 实现 {@link AppProps}：自加载 + 自描述。
 */
public class MailProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    /**
     * 排除的通知类型列表（denylist）。
     * <p>
     * 为空表示接收全部类型；非空时列表中的类型将被跳过不发送。
     */
    private List<String> excludedNotifyTypes;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getExcludedNotifyTypes() {
        return excludedNotifyTypes;
    }

    public void setExcludedNotifyTypes(List<String> excludedNotifyTypes) {
        this.excludedNotifyTypes = excludedNotifyTypes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        this.username = env.getProperty(ConfigKeys.NOTIFY_MAIL_USERNAME, String.class, username);
        this.password = env.getProperty(ConfigKeys.NOTIFY_MAIL_PASSWORD, String.class, password);
        List<String> types = env.getProperty(ConfigKeys.NOTIFY_MAIL_EXCLUDED_NOTIFY_TYPES, List.class);
        if (types != null) {
            this.excludedNotifyTypes = types;
        }
    }

    /**
     * 校验邮件通知配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        return Collections.emptyList();
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_MAIL_USERNAME, username));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_MAIL_PASSWORD, password, true));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_MAIL_EXCLUDED_NOTIFY_TYPES, excludedNotifyTypes));
        return entries;
    }

    @Override
    @JsonIgnore
    public List<SensitiveField> getSensitiveFields() {
        return Collections.singletonList(
            SensitiveField.secret("mail.password", () -> password, v -> this.password = v)
        );
    }
}
