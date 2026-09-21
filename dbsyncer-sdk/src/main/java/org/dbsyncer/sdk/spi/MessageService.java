package org.dbsyncer.sdk.spi;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.dbsyncer.sdk.notify.NotifyType;

import java.util.Map;

/**
 * 消息服务接口
 *
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-21 23:18
 */
public interface MessageService {

    /**
     * 发送消息
     *
     * @param title   标题
     * @param content 内容
     */
    void sendMessage(String title, String content);

    /**
     * 发送消息（带事件类型）
     *
     * @param title   标题
     * @param content 内容
     * @param type    事件类型
     */
    void sendMessage(String title, String content, NotifyType type);

    /**
     * 发送消息（带事件类型和通知配置）
     * <p>
     * ADR-0035：支持分组通知配置，调用方负责解析配置，MessageService 不耦合分组逻辑
     *
     * @param title   标题
     * @param content 内容
     * @param type    事件类型
     * @param props   通知配置，为 null 时走全局配置
     */
    default void sendMessage(String title, String content, NotifyType type, NotifyProps props) {
        sendMessage(title, content, type);
    }

    /**
     * 发送统一格式的通知消息（Markdown）
     * <p>
     * 统一模板：标题 + 操作类型 + Mapping ID + 任务名称 + 可选详情字段
     *
     * @param mapping       映射对象，为 null 时 ID 和名称显示为 unknown
     * @param operationType 操作/事件类型描述（如"手动操作提示"、"任务中断"、"数据同步异常"等）
     * @param type          通知类型
     * @param resolver      通知配置解析器
     * @param details       额外的详情字段（key=字段名, value=字段值），可为 null
     */
    default void sendNotifyMessage(Mapping mapping, String operationType, NotifyType type,
                                   NotifyConfigResolver resolver, Map<String, String> details) {
        String mappingId = mapping != null ? mapping.getId() : null;
        StringBuilder msg = new StringBuilder();
        msg.append("### DBSyncer ").append(operationType).append("\n\n");
        msg.append("**操作类型**: ").append(operationType).append("\n\n");
        msg.append("**Mapping ID**: ").append(mappingId != null ? mappingId : "unknown").append("\n\n");
        msg.append("**任务名称**: ").append(mapping != null ? mapping.getName() : "unknown").append("\n\n");
        if (details != null) {
            details.forEach((k, v) -> msg.append("**").append(k).append("**: ").append(v).append("\n\n"));
        }
        NotifyProps props = resolver.resolve(mappingId);
        sendMessage("dbsyncer", msg.toString(), type, props);
    }
}
