package org.dbsyncer.common.scheduled;

/**
 * 启动期定时任务注册 SPI。
 * <p>
 * 实现此接口的组件，由 PreloadTemplate 在配置完全加载后统一调用
 * {@link #registerScheduledTasks()}，确保定时任务注册时
 * ConfigRegistry 已从 DB 加载合并后的最终配置。
 * <p>
 * 替代原 @Scheduled / @PostConstruct 中的定时任务注册，
 * 避免在配置未加载时使用 props 默认值。
 */
public interface StartupTaskRegistrar {

    /**
     * 注册本组件的定时任务。
     * <p>
     * 在 PreloadTemplate 完成配置加载后调用，
     * 此时 ConfigRegistry.getConfig() 返回合并后的最终配置。
     */
    void registerScheduledTasks();
}
