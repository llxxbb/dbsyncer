package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.spi.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class MessageServiceImpl implements MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    /**
     * NotifyService 为可选注入，未配置时通过 CompositeNotifyService 兜底
     * 若 CompositeNotifyService.channels 为空，则所有通知直接跳过
     */
    @Autowired
    @Qualifier("compositeNotifyService")
    private NotifyService notifyService;

    @Resource
    private ProfileComponent profileComponent;

    @Override
    public void sendMessage(String title, String content) {
        sendMessage(title, content, null, null);
    }

    @Override
    public void sendMessage(String title, String content, NotifyType type) {
        sendMessage(title, content, type, null);
    }

    /**
     * 发送消息（带事件类型和通知配置）
     * <p>
     * ADR-0035：支持分组通知配置，调用方负责解析配置，MessageService 不耦合分组逻辑
     */
    @Override
    public void sendMessage(String title, String content, NotifyType type, NotifyProps props) {
        if (notifyService == null) {
            return;
        }

        UserConfig userConfig = profileComponent.getUserConfig();
        if (null == userConfig) {
            return;
        }

        List<String> mails = new ArrayList<>();
        userConfig.getUserInfoList().forEach(userInfo -> {
            if (StringUtil.isNotBlank(userInfo.getEmail())) {
                mails.addAll(Arrays.asList(StringUtil.split(userInfo.getEmail(), StringUtil.COMMA)));
            }
        });

        NotifyMessage msg = NotifyMessage.newBuilder()
                .setTitle(title)
                .setContent(content)
                .setReceivers(mails)
                .setType(type);
        logger.info("发送通知消息：{}", msg.getContent());

        // ADR-0035：使用传入的 props 发送
        notifyService.sendMessage(msg, props);
    }

}
