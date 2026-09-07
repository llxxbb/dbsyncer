package org.dbsyncer.parser.notify;

import org.dbsyncer.common.config.props.DingTalkProps;
import org.dbsyncer.common.config.props.MailProps;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.sdk.notify.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通知全局配置。
 * <p>
 * 配置统一从 ConfigRegistry 读取（NotifyProps，C 类）。
 * 启动时由 PreloadTemplate 触发 onConfigChanged 完成初始化。
 *
 * @author AE86
 * @version 1.0.0
 * @date 2026/06/03
 */
@Component
public class NotifyConfig implements ConfigChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(NotifyConfig.class);

    @Resource
    private ConfigRegistry configRegistry;

    /**
     * 启用的通知通道列表，如 mail, dingtalk
     */
    private List<String> channels = new ArrayList<>();

    /**
     * 邮件通道配置
     */
    private MailConfig mail = new MailConfig();

    /**
     * 钉钉通道配置
     */
    private DingTalkConfig dingtalk = new DingTalkConfig();

    @Override
    public void onConfigChanged(SystemConfig newConfig) {
        // 从 mergedConfig 读取最终配置（DB 优先，Environment 兜底）
        NotifyProps notify = configRegistry.getConfig().getNotify();
        if (notify == null) return;

        // channels
        if (notify.getChannels() != null) {
            this.channels = notify.getChannels();
        }

        // mail
        MailProps mailProps = notify.getMail();
        if (mailProps != null) {
            if (mailProps.getUsername() != null) {
                this.mail.setUsername(mailProps.getUsername());
            }
            if (mailProps.getPassword() != null) {
                this.mail.setPassword(mailProps.getPassword());
            }
            if (mailProps.getNotifyTypes() != null) {
                this.mail.setNotifyTypes(mailProps.getNotifyTypes());
            }
        }

        // dingtalk
        DingTalkProps dingtalkProps = notify.getDingtalk();
        if (dingtalkProps != null) {
            if (dingtalkProps.getWebhookUrl() != null) {
                this.dingtalk.setWebhookUrl(dingtalkProps.getWebhookUrl());
            }
            if (dingtalkProps.getSecret() != null) {
                this.dingtalk.setSecret(dingtalkProps.getSecret());
            }
            if (dingtalkProps.getNotifyTypes() != null) {
                this.dingtalk.setNotifyTypes(dingtalkProps.getNotifyTypes());
            }
        }
    }

    public boolean isChannelEnabled(String channel) {
        if (CollectionUtils.isEmpty(channels)) {
            return false;
        }
        return channels.contains(channel);
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public MailConfig getMail() {
        return mail;
    }

    public void setMail(MailConfig mail) {
        this.mail = mail;
    }

    public DingTalkConfig getDingtalk() {
        return dingtalk;
    }

    public void setDingtalk(DingTalkConfig dingtalk) {
        this.dingtalk = dingtalk;
    }

    /**
     * 邮件通道配置
     */
    public static class MailConfig {
        private String username;
        private String password;
        private List<String> notifyTypes = new ArrayList<>();
        private Set<NotifyType> notifyTypeSet;

        public boolean shouldNotify(NotifyType type) {
            if (notifyTypeSet == null) {
                notifyTypeSet = new HashSet<>();
                if (!CollectionUtils.isEmpty(notifyTypes)) {
                    for (String name : notifyTypes) {
                        try {
                            notifyTypeSet.add(NotifyType.valueOf(name));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            return notifyTypeSet.isEmpty() || type == null || notifyTypeSet.contains(type);
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public List<String> getNotifyTypes() { return notifyTypes; }
        public void setNotifyTypes(List<String> notifyTypes) {
            this.notifyTypes = notifyTypes;
            this.notifyTypeSet = null;  // ADR-0029 §4.5：失效懒加载缓存
        }
    }

    /**
     * 钉钉通道配置
     */
    public static class DingTalkConfig {
        private String webhookUrl;
        private String secret;
        private List<String> notifyTypes = new ArrayList<>();
        private Set<NotifyType> notifyTypeSet;

        public boolean shouldNotify(NotifyType type) {
            if (notifyTypeSet == null) {
                notifyTypeSet = new HashSet<>();
                if (!CollectionUtils.isEmpty(notifyTypes)) {
                    for (String name : notifyTypes) {
                        try {
                            notifyTypeSet.add(NotifyType.valueOf(name));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            return notifyTypeSet.isEmpty() || type == null || notifyTypeSet.contains(type);
        }

        public String getWebhookUrl() { return webhookUrl; }
        public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public List<String> getNotifyTypes() { return notifyTypes; }
        public void setNotifyTypes(List<String> notifyTypes) {
            this.notifyTypes = notifyTypes;
            this.notifyTypeSet = null;  // ADR-0029 §4.5：失效懒加载缓存
        }
    }
}
