package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 2 执行器配置。
 * <p>
 * 负责子 {@link PoolProps} 的创建、环境加载和配置条目生成。
 * 配置键名集中管理于此，子对象作为纯数据容器，
 * 避免 key 作为实例字段导致反序列化后丢失的问题。
 */
public class ExecutorProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    /** 通用执行器 → ExecutorFactory */
    private PoolProps general;

    /** 存储执行器 → ExecutorFactory */
    private PoolProps storage;

    /** 调度任务执行器 → ExecutorFactory */
    private PoolProps dispatch;

    public PoolProps getGeneral() {
        return general;
    }

    public void setGeneral(PoolProps general) {
        this.general = general;
    }

    public PoolProps getStorage() {
        return storage;
    }

    public void setStorage(PoolProps storage) {
        this.storage = storage;
    }

    public PoolProps getDispatch() {
        return dispatch;
    }

    public void setDispatch(PoolProps dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        this.general = readPool(env,
                ConfigKeys.EXECUTOR_GENERAL_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_GENERAL_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY);

        this.storage = readPool(env,
                ConfigKeys.EXECUTOR_STORAGE_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_STORAGE_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY);

        this.dispatch = readPool(env,
                ConfigKeys.EXECUTOR_DISPATCH_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_DISPATCH_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY);
    }

    private PoolProps readPool(Environment env, String coreKey, String maxKey, String queueKey) {
        PoolProps pool = new PoolProps();
        pool.setThreadCoreSize(env.getProperty(coreKey, Integer.class, pool.getThreadCoreSize()));
        pool.setMaxThreadSize(env.getProperty(maxKey, Integer.class, pool.getMaxThreadSize()));
        pool.setThreadQueueCapacity(env.getProperty(queueKey, Integer.class, pool.getThreadQueueCapacity()));
        return pool;
    }

    /**
     * 校验执行器配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (general != null) {
            errors.addAll(general.validate("通用执行器"));
        }
        if (storage != null) {
            errors.addAll(storage.validate("存储执行器"));
        }
        if (dispatch != null) {
            errors.addAll(dispatch.validate("调度任务执行器"));
        }
        return errors;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        addPoolEntries(entries, "通用执行器", general,
                ConfigKeys.EXECUTOR_GENERAL_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_GENERAL_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY);
        addPoolEntries(entries, "存储执行器", storage,
                ConfigKeys.EXECUTOR_STORAGE_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_STORAGE_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY);
        addPoolEntries(entries, "调度任务执行器", dispatch,
                ConfigKeys.EXECUTOR_DISPATCH_THREAD_CORE_SIZE,
                ConfigKeys.EXECUTOR_DISPATCH_MAX_THREAD_SIZE,
                ConfigKeys.EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY);
        return entries;
    }

    private void addPoolEntries(List<ConfigEntry> entries, String name, PoolProps pool,
                                String coreKey, String maxKey, String queueKey) {
        if (pool == null) return;
        entries.add(new ConfigEntry(coreKey, pool.getThreadCoreSize()));
        entries.add(new ConfigEntry(maxKey, pool.getMaxThreadSize()));
        entries.add(new ConfigEntry(queueKey, pool.getThreadQueueCapacity()));
    }
}
