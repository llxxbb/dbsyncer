package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
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
 * 实现 {@link EnvironmentLoadable}：自加载 + 自描述。
 */
public class MailProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private List<String> notifyTypes;

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

    public List<String> getNotifyTypes() {
        return notifyTypes;
    }

    public void setNotifyTypes(List<String> notifyTypes) {
        this.notifyTypes = notifyTypes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        this.username = env.getProperty(ConfigKeys.NOTIFY_MAIL_USERNAME, String.class, username);
        this.password = env.getProperty(ConfigKeys.NOTIFY_MAIL_PASSWORD, String.class, password);
        List<String> types = env.getProperty(ConfigKeys.NOTIFY_MAIL_NOTIFY_TYPES, List.class);
        if (types != null) {
            this.notifyTypes = types;
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
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_MAIL_NOTIFY_TYPES, notifyTypes));
        return entries;
    }

    @Override
    @JsonIgnore
    public List<SensitiveField> getSensitiveFields() {
        return Collections.singletonList(
            new SensitiveField("mail.password", () -> password, v -> this.password = v)
        );
    }
}
