package org.dbsyncer.parser.notify;

import okhttp3.*;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.DingTalkProps;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉 Webhook 通知服务
 */
@Component("dingTalkNotifyService")
public class DingTalkNotifyServiceProvider implements NotifyService {

    private static final Logger logger = LoggerFactory.getLogger(DingTalkNotifyServiceProvider.class);
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();

    @Resource
    private ConfigRegistry configRegistry;

    /**
     * 通知类型过滤缓存（懒加载，配置变更时置 null 失效）
     */
    private volatile Set<NotifyType> notifyTypeSet;

    private DingTalkProps getDingTalkProps() {
        if (configRegistry.getConfig().getNotify() == null) return null;
        return configRegistry.getConfig().getNotify().getDingtalk();
    }

    /**
     * 判断当前通知类型是否应发送（基于 notifyTypes 过滤）
     */
    private boolean shouldNotify(NotifyType type) {
        if (notifyTypeSet == null) {
            synchronized (this) {
                if (notifyTypeSet == null) {
                    Set<NotifyType> set = new HashSet<>();
                    DingTalkProps cfg = getDingTalkProps();
                    if (cfg != null && !CollectionUtils.isEmpty(cfg.getNotifyTypes())) {
                        for (String name : cfg.getNotifyTypes()) {
                            try {
                                set.add(NotifyType.valueOf(name));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    notifyTypeSet = set;
                }
            }
        }
        return notifyTypeSet.isEmpty() || type == null || notifyTypeSet.contains(type);
    }

    @Override
    public void sendMessage(NotifyMessage msg) {
        if (msg == null || StringUtil.isBlank(msg.getContent())) return;

        DingTalkProps cfg = getDingTalkProps();
        if (cfg == null || !shouldNotify(msg.getType()) || StringUtil.isBlank(cfg.getWebhookUrl())) return;

        // 添加发送时间到内容
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String contentWithTime = "**发送时间：** " + timestamp + "\n\n" + msg.getContent();

        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", "DBSyncer");
            markdown.put("text", contentWithTime);

            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "markdown");
            payload.put("markdown", markdown);

            String body = JsonUtil.objToJson(payload);

            Response response = HTTP_CLIENT.newCall(new Request.Builder()
                    .url(cfg.getWebhookUrl())
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                    .build()).execute();

            logger.debug("DingTalk response: {}", response.isSuccessful() ? response.body() : "error");
            response.close();
        } catch (Exception e) {
            logger.error("DingTalk send error", e);
        }
    }

    /**
     * 发送通知消息，使用显式传入的 NotifyProps。
     * <p>
     * ADR-0035：覆盖接口默认实现，从 NotifyProps 中提取 DingTalkProps 发送。
     */
    @Override
    public void sendMessage(NotifyMessage msg, NotifyProps props) {
        if (props == null || props.getDingtalk() == null) {
            logger.warn("钉钉配置为空，消息发送已跳过");
            return;
        }
        DingTalkProps dingtalkProps = props.getDingtalk();
        if (msg == null || StringUtil.isBlank(msg.getContent())) return;
        if (StringUtil.isBlank(dingtalkProps.getWebhookUrl())) {
            logger.warn("钉钉配置为空，消息发送已跳过");
            return;
        }

        // 检查通知类型过滤（使用传入的配置）
        if (!shouldNotifyWithType(msg.getType(), dingtalkProps.getNotifyTypes())) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String contentWithTime = "**发送时间：** " + timestamp + "\n\n" + msg.getContent();

        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", "DBSyncer");
            markdown.put("text", contentWithTime);

            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "markdown");
            payload.put("markdown", markdown);

            String body = JsonUtil.objToJson(payload);

            Response response = HTTP_CLIENT.newCall(new Request.Builder()
                    .url(dingtalkProps.getWebhookUrl())
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                    .build()).execute();

            logger.debug("DingTalk response (group config): {}", response.isSuccessful() ? response.body() : "error");
            response.close();
        } catch (Exception e) {
            logger.error("DingTalk send error", e);
        }
    }

    /**
     * 判断当前通知类型是否应发送（基于指定的 notifyTypes 过滤）。
     */
    private boolean shouldNotifyWithType(NotifyType type, List<String> notifyTypes) {
        if (notifyTypes == null || notifyTypes.isEmpty()) {
            return true;
        }
        if (type == null) {
            return true;
        }
        String typeName = type.name();
        for (String name : notifyTypes) {
            if (typeName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
