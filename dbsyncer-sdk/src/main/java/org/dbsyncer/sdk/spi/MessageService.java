package org.dbsyncer.sdk.spi;

import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.sdk.notify.NotifyType;

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
}
