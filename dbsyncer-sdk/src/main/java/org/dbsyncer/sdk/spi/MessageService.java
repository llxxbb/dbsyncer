package org.dbsyncer.sdk.spi;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.sdk.model.Mapping;
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
     * 发送统一格式的通知消息（Markdown）
     * <p>
     * 统一模板：标题 + 操作类型 + Mapping ID + 任务名称 + 可选详情字段
     * <p>
     * 通知配置由实现类内部解析，调用方无需关心分组逻辑
     *
     * @param mapping       映射对象，为 null 时 ID 和名称显示为 unknown
     * @param operationType 操作/事件类型描述（如"手动操作提示"、"任务中断"、"数据同步异常"等）
     * @param type          通知类型
     * @param details       额外的详情字段（key=字段名, value=字段值），可为 null
     */
    void sendNotifyMessage(Mapping mapping, String operationType, NotifyType type, Map<String, String> details);
}
