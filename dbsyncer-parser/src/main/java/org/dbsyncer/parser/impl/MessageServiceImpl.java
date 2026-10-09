package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
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
import java.util.Map;

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

    @Resource
    private NotifyConfigResolver notifyConfigResolver;

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
     * props 为 null 时自动从 NotifyConfigResolver 获取全局配置，避免静默跳过
     */
    private void sendMessage(String title, String content, NotifyType type, NotifyProps props) {
        if (notifyService == null) {
            return;
        }

        // props 为 null 时自动获取全局配置
        if (props == null && notifyConfigResolver != null) {
            props = notifyConfigResolver.resolve(null);
        }

        List<String> mails = new ArrayList<>();
        UserConfig userConfig = profileComponent.getUserConfig();
        if (null != userConfig) {
            userConfig.getUserInfoList().forEach(userInfo -> {
                if (StringUtil.isNotBlank(userInfo.getEmail())) {
                    mails.addAll(Arrays.asList(StringUtil.split(userInfo.getEmail(), StringUtil.COMMA)));
                }
            });
        }

        NotifyMessage msg = NotifyMessage.newBuilder()
                .setTitle(title)
                .setContent(content)
                .setReceivers(mails)
                .setType(type);
        logger.info("发送通知消息：{}", msg.getContent());

        // ADR-0035：使用传入的 props 发送
        notifyService.sendMessage(msg, props);
    }

    /**
     * 发送统一格式的通知消息（Markdown）
     * <p>
     * 统一模板：标题 + 操作类型 + Mapping ID + 任务名称 + 可选详情字段
     * 通知配置由实现类内部解析，调用方无需关心分组逻辑
     */
    @Override
    public void sendNotifyMessage(Mapping mapping, String operationType, NotifyType type, Map<String, String> details) {
        String mappingId = mapping != null ? mapping.getId() : null;
        StringBuilder msg = new StringBuilder();
        msg.append("### DBSyncer ").append(operationType).append("\n\n");
        msg.append("**操作类型**: ").append(operationType).append("\n\n");
        msg.append("**Mapping ID**: ").append(mappingId != null ? mappingId : "unknown").append("\n\n");
        msg.append("**任务名称**: ").append(mapping != null ? mapping.getName() : "unknown").append("\n\n");
        if (details != null) {
            details.forEach((k, v) -> msg.append("**").append(k).append("**: ").append(v).append("\n\n"));
        }
        NotifyProps props = notifyConfigResolver != null ? notifyConfigResolver.resolve(mappingId) : null;
        sendMessage("dbsyncer", msg.toString(), type, props);
    }

}
