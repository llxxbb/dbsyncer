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
 * 钉钉通知子配置。
 * <p>
 * 实现 {@link AppProps}：自加载 + 自描述。
 */
public class DingTalkProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    private String webhookUrl;
    private String secret;
    /**
     * 排除的通知类型列表（denylist）。
     * <p>
     * 为空表示接收全部类型；非空时列表中的类型将被跳过不发送。
     */
    private List<String> excludedNotifyTypes;

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

    public List<String> getExcludedNotifyTypes() {
        return excludedNotifyTypes;
    }

    public void setExcludedNotifyTypes(List<String> excludedNotifyTypes) {
        this.excludedNotifyTypes = excludedNotifyTypes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        this.webhookUrl = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_WEBHOOK_URL, String.class, webhookUrl);
        this.secret = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_SECRET, String.class, secret);
        List<String> types = env.getProperty(ConfigKeys.NOTIFY_DINGTALK_EXCLUDED_NOTIFY_TYPES, List.class);
        if (types != null) {
            this.excludedNotifyTypes = types;
        }
    }

    /**
     * 校验钉钉通知配置，返回错误信息列表。
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
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_WEBHOOK_URL, webhookUrl, true));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_SECRET, secret, true));
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_DINGTALK_EXCLUDED_NOTIFY_TYPES, excludedNotifyTypes));
        return entries;
    }

    @Override
    @JsonIgnore
    public List<SensitiveField> getSensitiveFields() {
        return Collections.singletonList(
            SensitiveField.secret("dingtalk.secret", () -> secret, v -> this.secret = v)
        );
    }
}
