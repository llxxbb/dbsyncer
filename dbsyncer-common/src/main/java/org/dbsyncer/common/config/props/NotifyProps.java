package org.dbsyncer.common.config.props;

import java.io.Serializable;
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
}
