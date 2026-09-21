package org.dbsyncer.parser.notify;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 组合通知服务，聚合多个通知通道
 * <p>
 * 通过 ConfigRegistry 的 channels 配置控制启用哪些通道：
 * - mail → MailNotifyServiceProvider
 * - dingtalk → DingTalkNotifyServiceProvider
 * <p>
 * 没有配置任何通道时，channels 为空，所有发送操作直接跳过。
 *
 * @author AE86
 * @version 1.0.0
 * @date 2026/06/03
 */
@Primary
@Component
public class CompositeNotifyService implements NotifyService {

    private static final Logger logger = LoggerFactory.getLogger(CompositeNotifyService.class);

    @Resource
    private ConfigRegistry configRegistry;

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
        sendMessage(notifyMessage, configRegistry.getConfig().getNotify());
    }

    /**
     * 发送通知消息，使用显式传入的 NotifyProps。
     * <p>
     * ADR-0035：支持分组通知配置，按通道逐一发送。
     */
    @Override
    public void sendMessage(NotifyMessage notifyMessage, NotifyProps props) {
        if (props == null) {
            logger.warn("未配置任何通知配置，消息发送已跳过");
            return;
        }
        List<String> channels = props.getChannels();
        if (CollectionUtils.isEmpty(channels)) {
            logger.warn("未配置任何通知通道，消息发送已跳过");
            return;
        }

        // 按通道逐一发送
        if (channels.contains("mail")) {
            try {
                if (mailNotifyService != null && props.getMail() != null) {
                    mailNotifyService.sendMessage(notifyMessage, props);
                }
            } catch (Exception e) {
                logger.error("邮件通知发送失败", e);
            }
        }

        if (channels.contains("dingtalk")) {
            try {
                if (dingTalkNotifyService != null && props.getDingtalk() != null) {
                    dingTalkNotifyService.sendMessage(notifyMessage, props);
                }
            } catch (Exception e) {
                logger.error("钉钉通知发送失败", e);
            }
        }
    }
}
