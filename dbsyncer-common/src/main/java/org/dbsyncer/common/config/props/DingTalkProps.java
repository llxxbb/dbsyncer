package org.dbsyncer.common.config.props;

import java.io.Serializable;
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
}
