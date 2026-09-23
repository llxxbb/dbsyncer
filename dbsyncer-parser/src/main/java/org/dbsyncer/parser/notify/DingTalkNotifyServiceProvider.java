package org.dbsyncer.parser.notify;

import okhttp3.*;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.DingTalkProps;
import org.dbsyncer.common.config.props.NotifyProps;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private DingTalkProps getDingTalkProps() {
        if (configRegistry.getConfig().getNotify() == null) return null;
        return configRegistry.getConfig().getNotify().getDingtalk();
    }

    /**
     * 判断当前通知类型是否应发送（基于 excludedNotifyTypes 排除列表）。
     * <p>
     * excludedNotifyTypes 为空表示全部放行；type 为 null 也放行；
     * 命中排除列表的类型将被跳过。
     */
    private boolean shouldNotify(NotifyType type, List<String> excludedNotifyTypes) {
        if (excludedNotifyTypes == null || excludedNotifyTypes.isEmpty()) {
            return true;
        }
        if (type == null) {
            return true;
        }
        for (String name : excludedNotifyTypes) {
            if (StringUtil.equalsIgnoreCase(type.name(), name)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void sendMessage(NotifyMessage msg) {
        sendMessage(msg, configRegistry.getConfig().getNotify());
    }

    /**
     * 发送通知消息，使用显式传入的 NotifyProps。
     * <p>
     * ADR-0035：覆盖接口默认实现，从 NotifyProps 中提取 DingTalkProps 发送。
     */
    @Override
    public void sendMessage(NotifyMessage msg, NotifyProps props) {
        DingTalkProps cfg = extractAndValidate(msg, props);
        if (cfg == null) return;
        doSend(cfg.getWebhookUrl(), msg);
    }

    /**
     * 提取 DingTalkProps 并校验必填项。
     *
     * @return DingTalkProps，校验失败返回 null
     */
    private DingTalkProps extractAndValidate(NotifyMessage msg, NotifyProps props) {
        if (msg == null || StringUtil.isBlank(msg.getContent())) return null;
        DingTalkProps cfg = props != null ? props.getDingtalk() : getDingTalkProps();
        if (cfg == null || StringUtil.isBlank(cfg.getWebhookUrl())) return null;
        if (!shouldNotify(msg.getType(), cfg.getExcludedNotifyTypes())) return null;
        return cfg;
    }

    /**
     * 构建 Markdown 内容并通过 Webhook 发送。
     */
    private void doSend(String webhookUrl, NotifyMessage msg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String contentWithTime = "**发送时间：** " + timestamp + "\n\n" + msg.getContent();

        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", "DBSyncer");
        markdown.put("text", contentWithTime);

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "markdown");
        payload.put("markdown", markdown);

        try {
            String body = JsonUtil.objToJson(payload);
            Response response = HTTP_CLIENT.newCall(new Request.Builder()
                    .url(webhookUrl)
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
                    .build()).execute();
            logger.debug("DingTalk response: code={}", response.code());
            response.close();
        } catch (Exception e) {
            logger.error("DingTalk send error", e);
        }
    }
}
