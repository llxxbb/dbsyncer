package org.dbsyncer.parser.listener;

import org.dbsyncer.parser.model.SystemConfig;

/**
 * 配置变更监听器（ADR-0030）。
 * <p>
 * 当 SystemConfigRegistry 合并后的配置发生变更时，
 * 所有实现了此接口的 Spring Bean 会收到通知，
 * 自行决定如何更新内部状态和清理缓存。
 * <p>
 * 实现方应从 newConfig 中提取自己关心的子配置，更新内部字段，
 * 并清理相关缓存（如 KeywordMatcher、notifyTypeSet 等）。
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
