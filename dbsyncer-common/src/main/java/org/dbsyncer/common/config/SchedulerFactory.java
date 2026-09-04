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
 */
public class SchedulerFactory implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerFactory.class);

    private final IntSupplier poolSizeSupplier;

    private volatile ThreadPoolTaskScheduler taskScheduler;

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
        scheduler.setAwaitTerminationSeconds(30);
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

    @Override
    public void destroy() {
        if (taskScheduler != null) {
            logger.info("[SchedulerFactory] 正在关闭 taskScheduler");
            taskScheduler.shutdown();
        }
    }
}
