package org.dbsyncer.sdk.notify;

import org.dbsyncer.common.config.props.NotifyProps;

/**
 * 通知配置解析器
 * <p>
 * 根据 mappingId 解析对应的通知配置（分组配置 + 全局兜底）
 * <p>
 * ADR-0035：职责分离，配置解析是业务逻辑，不应耦合在通知服务中
 */
public interface NotifyConfigResolver {

    /**
     * 解析通知配置
     *
     * @param mappingId 映射 ID，为 null 时返回全局配置
     * @return 通知配置
     */
    NotifyProps resolve(String mappingId);
}
