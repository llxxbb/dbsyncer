package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 钉钉通知子配置。
 * <p>
 * 实现 {@link EnvironmentLoadable}：自加载 + 自描述。
 */
public class DingTalkProps implements Serializable, EnvironmentLoadable {

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

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        this.webhookUrl = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_WEBHOOK_URL, String.class);
        this.secret = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_SECRET, String.class);
        List<String> types = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_NOTIFY_TYPES, List.class);
        if (types != null) {
            this.notifyTypes = types;
        }
    }

    /**
     * 校验钉钉通知配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        return Collections.emptyList();
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_WEBHOOK_URL, webhookUrl, true));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_SECRET, secret, true));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_NOTIFY_TYPES, notifyTypes));
        return entries;
    }
}
