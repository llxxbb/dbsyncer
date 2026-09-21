package org.dbsyncer.sdk.notify;

import org.dbsyncer.common.config.props.NotifyProps;

/**
 * 通知服务（同步异常、连接器异常、应用异常等消息）
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/11/13 22:07
 */
public interface NotifyService {

    /**
     * 发送通知消息
     */
    void sendMessage(NotifyMessage notifyMessage);

    /**
     * 发送通知消息，使用显式传入的通知配置。
     * <p>
     * ADR-0035：支持分组通知配置，默认实现委托给无参版本。
     * 实现类可覆盖此方法以使用自定义配置发送。
     *
     * @param notifyMessage 通知消息
     * @param props         通知配置，为 null 时跳过发送
     */
    default void sendMessage(NotifyMessage notifyMessage, NotifyProps props) {
        if (props == null) {
            return;
        }
        sendMessage(notifyMessage);
    }
}
