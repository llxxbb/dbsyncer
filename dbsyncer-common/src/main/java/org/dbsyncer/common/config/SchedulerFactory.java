package org.dbsyncer.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.function.IntSupplier;

/**
 * ADR-0032：延迟调度线程池工厂。
 * <p>
 * 替代原 SchedulerConfig 的 @Bean 创建。
 * 线程池在首次使用时才创建，确保此时 ConfigRegistry 已从 DB 加载配置。
 * <p>
 * 通过 IntSupplier 注入配置来源，避免 common 模块依赖 parser 模块。
 * <p>
 * ADR-0033：支持 schedulerPoolSize 热重载。
 * 提供 {@link #reload()} 方法，由 ScheduledTaskServiceImpl 在
 * ConfigChangeListener 链中统一驱动（检测变更 → reload → 重注册任务）。
 */
public class SchedulerFactory implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerFactory.class);

    private final IntSupplier poolSizeSupplier;

    private volatile ThreadPoolTaskScheduler taskScheduler;

    /** 当前生效的 poolSize，用于变更检测 */
    private volatile int currentPoolSize = -1;

    public SchedulerFactory(IntSupplier poolSizeSupplier) {
        this.poolSizeSupplier = poolSizeSupplier;
    }

    /**
     * 获取调度线程池（DCL 延迟创建）。
     * <p>
     * 首次调用时从 Supplier 读取 poolSize，此时 ConfigRegistry 已加载 DB 配置。
     */
    public ThreadPoolTaskScheduler getTaskScheduler() {
        if (taskScheduler == null) {
            synchronized (this) {
                if (taskScheduler == null) {
                    int poolSize = poolSizeSupplier.getAsInt();
                    taskScheduler = createScheduler(poolSize);
                    currentPoolSize = poolSize;
                    logger.info("[SchedulerFactory] taskScheduler 已创建: poolSize={}", poolSize);
                }
            }
        }
        return taskScheduler;
    }

    private ThreadPoolTaskScheduler createScheduler(int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(poolSize, 1));
        scheduler.setThreadNamePrefix("taskScheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setRejectedExecutionHandler((r, executor) -> {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        scheduler.initialize();
        return scheduler;
    }

    /**
     * ADR-0033：热重载。
     * <p>
     * 关闭旧调度器并置 null，下次 getTaskScheduler() 时 DCL 从 Supplier 读新值重建。
     * 调度器已配置 waitForTasksToCompleteOnShutdown=true, awaitTerminationSeconds=5，
     * shutdown() 会等待当前正在执行的任务完成（最多 5 秒）。
     * <p>
     * <b>注意</b>：reload 后已注册的定时任务（ScheduledFuture）会丢失，
     * 需要调用方重新注册。由 ScheduledTaskServiceImpl 负责。
     */
    public synchronized void reload() {
        if (taskScheduler != null) {
            logger.info("[SchedulerFactory] 开始热重载，关闭旧调度器...");
            taskScheduler.shutdown();
            taskScheduler = null;
            currentPoolSize = -1;
            logger.info("[SchedulerFactory] 热重载完成，调度器将在下次使用时重建");
        } else {
            logger.info("[SchedulerFactory] 调度器尚未创建，无需重载");
        }
    }

    /**
     * 获取当前生效的 poolSize，未创建或已 reload 时返回 -1。
     */
    public int getCurrentPoolSize() {
        return currentPoolSize;
    }

    @Override
    public void destroy() {
        if (taskScheduler != null) {
            logger.info("[SchedulerFactory] 正在关闭 taskScheduler");
            taskScheduler.shutdown();
        }
    }
}
