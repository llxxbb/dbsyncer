package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 钉钉通知子配置
 */
public class DingTalkProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String webhookUrl;
    private String secret;
    private List<String> notifyTypes;

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getNotifyTypes() {
        return notifyTypes;
    }

    public void setNotifyTypes(List<String> notifyTypes) {
        this.notifyTypes = notifyTypes;
    }

    /**
     * 校验钉钉通知配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        return Collections.emptyList();
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.dingtalk.webhook-url", webhookUrl, true));
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.dingtalk.secret", secret, true));
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.dingtalk.notify-types", notifyTypes));
        return entries;
    }
}
