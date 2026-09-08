package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 5 通知通道配置。
 * <p>
 * 创建子 Props 后委托子级完成自加载。
 */
public class NotifyProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private List<String> channels;
    private MailProps mail;
    private DingTalkProps dingtalk;

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
}
