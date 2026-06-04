package org.dbsyncer.plugin.impl;

import okhttp3.*;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.plugin.NotifyService;
import org.dbsyncer.plugin.config.NotifyConfig;
import org.dbsyncer.plugin.model.NotifyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
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
    private NotifyConfig notifyConfig;

    @Override
    public void sendMessage(NotifyMessage msg) {
        if (msg == null || StringUtil.isBlank(msg.getContent())) return;
        
        NotifyConfig.DingTalkConfig cfg = notifyConfig.getDingtalk();
        if (!cfg.shouldNotify(msg.getType()) || StringUtil.isBlank(cfg.getWebhookUrl())) return;

        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", "DBSyncer");
            markdown.put("text", msg.getContent());
            
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
}
