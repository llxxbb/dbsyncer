package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 5 通知通道配置
 */
public class NotifyProps implements Serializable {

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

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("dbsyncer.plugin.notify.channels", channels));
        if (mail != null) {
            entries.addAll(mail.toConfigEntries());
        }
        if (dingtalk != null) {
            entries.addAll(dingtalk.toConfigEntries());
        }
        return entries;
    }
}
