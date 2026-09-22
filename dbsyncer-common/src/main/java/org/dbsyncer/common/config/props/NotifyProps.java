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
 * Tab 5 通知通道配置。
 * <p>
 * 创建子 Props 后委托子级完成自加载。
 */
public class NotifyProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    /**
     * 是否启用独立配置（仅分组配置使用）。
     * <p>
     * false 或 null：走全局配置<br/>
     * true：使用本分组的通道配置（通道为空则不发通知）
     */
    private Boolean enabled;

    private List<String> channels;
    private MailProps mail;
    private DingTalkProps dingtalk;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public MailProps getMail() {
        return mail;
    }

    public void setMail(MailProps mail) {
        this.mail = mail;
    }

    public DingTalkProps getDingtalk() {
        return dingtalk;
    }

    public void setDingtalk(DingTalkProps dingtalk) {
        this.dingtalk = dingtalk;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        List<String> notifyChannels = env.getProperty(ConfigKeys.NOTIFY_CHANNELS, List.class);
        if (notifyChannels != null) {
            this.channels = notifyChannels;
        }

        MailProps mailProps = new MailProps();
        mailProps.loadFromEnvironment(env);
        this.mail = mailProps;

        DingTalkProps dingtalkProps = new DingTalkProps();
        dingtalkProps.loadFromEnvironment(env);
        this.dingtalk = dingtalkProps;
    }

    /**
     * 校验通知通道配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (mail != null) {
            errors.addAll(mail.validate());
        }
        if (dingtalk != null) {
            errors.addAll(dingtalk.validate());
        }
        return errors;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.NOTIFY_CHANNELS, channels));
        if (mail != null) {
            entries.addAll(mail.toConfigEntries());
        }
        if (dingtalk != null) {
            entries.addAll(dingtalk.toConfigEntries());
        }
        return entries;
    }

    /**
     * 递归收集所有敏感字段（包括子级 MailProps、DingTalkProps）。
     */
    @Override
    @JsonIgnore
    public List<SensitiveField> getSensitiveFields() {
        List<SensitiveField> fields = new ArrayList<>();
        if (mail != null) {
            fields.addAll(mail.getSensitiveFields());
        }
        if (dingtalk != null) {
            fields.addAll(dingtalk.getSensitiveFields());
        }
        return fields;
    }

}
