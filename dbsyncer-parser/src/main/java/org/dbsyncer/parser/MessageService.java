package org.dbsyncer.parser;

import org.dbsyncer.plugin.model.NotifyType;

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
}