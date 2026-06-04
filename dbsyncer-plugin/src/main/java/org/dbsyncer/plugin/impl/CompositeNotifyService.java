package org.dbsyncer.plugin.impl;

import org.dbsyncer.plugin.NotifyService;
import org.dbsyncer.plugin.config.NotifyConfig;
import org.dbsyncer.plugin.model.NotifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 组合通知服务，聚合多个通知通道
 * <p>
 * 通过 NotifyConfig.channels 配置控制启用哪些通道：
 * - mail → MailNotifyServiceProvider
 * - dingtalk → DingTalkNotifyServiceProvider
 * <p>
 * 没有配置任何通道时，channels 为空，所有发送操作直接跳过。
 *
 * @author AE86
 * @version 1.0.0
 * @date 2026/06/03
 */
@Component
public class CompositeNotifyService implements NotifyService {

    private static final Logger logger = LoggerFactory.getLogger(CompositeNotifyService.class);

    @Resource
    private NotifyConfig notifyConfig;

    /**
     * 邮件通知（可选注入）
     */
    @Autowired(required = false)
    @Qualifier("mailNotifyService")
    private NotifyService mailNotifyService;

    /**
     * 钉钉通知（可选注入）
     */
    @Autowired(required = false)
    @Qualifier("dingTalkNotifyService")
    private NotifyService dingTalkNotifyService;

    @Override
    public void sendMessage(NotifyMessage notifyMessage) {
        if (notifyConfig.getChannels() == null || notifyConfig.getChannels().isEmpty()) {
            return;
        }

        // 按通道逐一发送
        if (notifyConfig.isChannelEnabled("mail")) {
            try {
                if (mailNotifyService != null) {
                    mailNotifyService.sendMessage(notifyMessage);
                }
            } catch (Exception e) {
                logger.error("邮件通知发送失败", e);
            }
        }

        if (notifyConfig.isChannelEnabled("dingtalk")) {
            try {
                if (dingTalkNotifyService != null) {
                    dingTalkNotifyService.sendMessage(notifyMessage);
                }
            } catch (Exception e) {
                logger.error("钉钉通知发送失败", e);
            }
        }
    }
}
