package org.dbsyncer.parser.impl;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.MessageService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.plugin.model.NotifyMessage;
import org.dbsyncer.plugin.model.NotifyType;
import org.slf4j.LoggerFactory;
import org.dbsyncer.plugin.NotifyService;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;

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
        sendMessage(title, content, null);
    }

    @Override
    public void sendMessage(String title, String content, NotifyType type) {
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
        notifyService.sendMessage(msg);
    }

}
