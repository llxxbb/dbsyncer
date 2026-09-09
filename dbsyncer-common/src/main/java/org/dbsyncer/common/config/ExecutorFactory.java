package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.ExecutorProps;
import org.dbsyncer.common.config.props.PoolProps;
import org.dbsyncer.common.util.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.function.Supplier;

/**
 * ADR-0030：延迟线程池工厂。
 * <p>
 * 替代原 GeneralBufferConfig / StorageConfig / DispatchTaskConfig 的 @Bean 线程池创建。
 * 线程池在首次使用时才创建，确保此时 ConfigRegistry 已从 DB 加载配置。
 * <p>
 * 通过 Supplier 注入配置来源，避免 common 模块依赖 parser 模块。
 */
public class ExecutorFactory implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorFactory.class);

    private static final int DEFAULT_CORE = 4;
    private static final int DEFAULT_MAX = 8;
    private static final int DEFAULT_QUEUE = 1000;

    private final Supplier<ExecutorProps> executorPropsSupplier;

    private volatile ThreadPoolTaskExecutor generalExecutor;
    private volatile ThreadPoolTaskExecutor storageExecutor;
    private volatile ThreadPoolTaskExecutor dispatchExecutor;

    public ExecutorFactory(Supplier<ExecutorProps> executorPropsSupplier) {
        this.executorPropsSupplier = executorPropsSupplier;
    }

    public ThreadPoolTaskExecutor getGeneralExecutor() {
        if (generalExecutor == null) {
            synchronized (this) {
                if (generalExecutor == null) {
                    PoolProps pool = executorPropsSupplier.get().getGeneral();
                    generalExecutor = createPool(pool, defaultGeneralCore(), defaultGeneralMax(), "GeneralExecutor-");
                    logger.info("[ExecutorFactory] generalExecutor 已创建: core={}, max={}, queue={}",
                            getCore(pool, defaultGeneralCore()), getMax(pool, defaultGeneralMax()), getQueue(pool, DEFAULT_QUEUE));
                }
            }
        }
        return generalExecutor;
    }

    public ThreadPoolTaskExecutor getStorageExecutor() {
        if (storageExecutor == null) {
            synchronized (this) {
                if (storageExecutor == null) {
                    PoolProps pool = executorPropsSupplier.get().getStorage();
                    storageExecutor = createPool(pool, defaultStorageCore(), defaultStorageMax(), "StorageExecutor-");
                    logger.info("[ExecutorFactory] storageExecutor 已创建: core={}, max={}, queue={}",
                            getCore(pool, defaultStorageCore()), getMax(pool, defaultStorageMax()), getQueue(pool, DEFAULT_QUEUE));
                }
            }
        }
        return storageExecutor;
    }

    public ThreadPoolTaskExecutor getDispatchExecutor() {
        if (dispatchExecutor == null) {
            synchronized (this) {
                if (dispatchExecutor == null) {
                    PoolProps pool = executorPropsSupplier.get().getDispatch();
                    dispatchExecutor = createPool(pool, DEFAULT_CORE, DEFAULT_MAX, "DispatchTaskExecutor-");
                    logger.info("[ExecutorFactory] dispatchExecutor 已创建: core={}, max={}, queue={}",
                            getCore(pool, DEFAULT_CORE), getMax(pool, DEFAULT_MAX), getQueue(pool, 64));
                }
            }
        }
        return dispatchExecutor;
    }

    private ThreadPoolTaskExecutor createPool(PoolProps pool, int defCore, int defMax, String prefix) {
        int core = getCore(pool, defCore);
        int max = getMax(pool, defMax);
        int queue = getQueue(pool, DEFAULT_QUEUE);
        core = Math.min(core, max);
        return ThreadPoolUtil.newThreadPoolTaskExecutor(core, max, queue, 30, prefix);
    }

    private int getCore(PoolProps pool, int fallback) {
        return pool != null && pool.getThreadCoreSize() != null ? pool.getThreadCoreSize() : fallback;
    }

    private int getMax(PoolProps pool, int fallback) {
        return pool != null && pool.getMaxThreadSize() != null ? pool.getMaxThreadSize() : fallback;
    }

    private int getQueue(PoolProps pool, int fallback) {
        return pool != null && pool.getThreadQueueCapacity() != null ? pool.getThreadQueueCapacity() : fallback;
    }

    private int defaultGeneralCore() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    private int defaultGeneralMax() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    private int defaultStorageCore() {
        return Runtime.getRuntime().availableProcessors();
    }

    private int defaultStorageMax() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public void destroy() {
        shutdown(generalExecutor, "generalExecutor");
        shutdown(storageExecutor, "storageExecutor");
        shutdown(dispatchExecutor, "dispatchExecutor");
    }

    private void shutdown(ThreadPoolTaskExecutor executor, String name) {
        if (executor != null) {
            logger.info("[ExecutorFactory] 正在关闭 {}", name);
            executor.shutdown();
        }
    }
}
