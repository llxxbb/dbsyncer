package org.dbsyncer.sdk.notify;

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
}
