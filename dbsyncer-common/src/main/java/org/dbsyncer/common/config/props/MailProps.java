package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * 邮件通知子配置
 */
public class MailProps implements Serializable {

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

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.mail.username", username));
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.mail.password", password, true));
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.mail.notify-types", notifyTypes));
        return entries;
    }
}
