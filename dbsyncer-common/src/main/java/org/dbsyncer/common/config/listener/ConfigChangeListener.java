package org.dbsyncer.common.config.listener;

import org.dbsyncer.common.model.SystemConfig;

/**
 * 配置变更监听器（ADR-0030）。
 * <p>
 * 从 dbsyncer-parser 迁移到 dbsyncer-common，使底层模块可直接监听配置变更。
 * <p>
 * 当 ConfigRegistry 合并后的配置发生变更时，
 * 所有实现了此接口的 Spring Bean 会收到通知，
 * 自行决定如何更新内部状态和清理缓存。
 *
 * @author DBSyncer
 */
public interface ConfigChangeListener {

    /**
     * 配置变更回调。
     *
     * @param newConfig 合并后的最新系统配置
     */
    void onConfigChanged(SystemConfig newConfig);
}
