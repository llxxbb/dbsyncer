package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 2 执行器配置
 */
public class ExecutorProps implements Serializable, EnvironmentLoadable {

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
        PoolProps generalPool = new PoolProps();
        generalPool.setThreadCoreSize(env.getProperty(ConfigKeys.EXECUTOR_GENERAL_THREAD_CORE_SIZE, Integer.class));
        generalPool.setMaxThreadSize(env.getProperty(ConfigKeys.EXECUTOR_GENERAL_MAX_THREAD_SIZE, Integer.class));
        generalPool.setThreadQueueCapacity(env.getProperty(ConfigKeys.EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY, Integer.class));
        this.general = generalPool;

        PoolProps storagePool = new PoolProps();
        storagePool.setThreadCoreSize(env.getProperty(ConfigKeys.EXECUTOR_STORAGE_THREAD_CORE_SIZE, Integer.class));
        storagePool.setMaxThreadSize(env.getProperty(ConfigKeys.EXECUTOR_STORAGE_MAX_THREAD_SIZE, Integer.class));
        storagePool.setThreadQueueCapacity(env.getProperty(ConfigKeys.EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY, Integer.class));
        this.storage = storagePool;

        PoolProps dispatchPool = new PoolProps();
        dispatchPool.setThreadCoreSize(env.getProperty(ConfigKeys.EXECUTOR_DISPATCH_THREAD_CORE_SIZE, Integer.class));
        dispatchPool.setMaxThreadSize(env.getProperty(ConfigKeys.EXECUTOR_DISPATCH_MAX_THREAD_SIZE, Integer.class));
        dispatchPool.setThreadQueueCapacity(env.getProperty(ConfigKeys.EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY, Integer.class));
        this.dispatch = dispatchPool;
    }

    /**
     * 校验执行器配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
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

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        if (general != null) {
            entries.addAll(general.toConfigEntries(
                    ConfigKeys.EXECUTOR_GENERAL_THREAD_CORE_SIZE,
                    ConfigKeys.EXECUTOR_GENERAL_MAX_THREAD_SIZE,
                    ConfigKeys.EXECUTOR_GENERAL_THREAD_QUEUE_CAPACITY));
        }
        if (storage != null) {
            entries.addAll(storage.toConfigEntries(
                    ConfigKeys.EXECUTOR_STORAGE_THREAD_CORE_SIZE,
                    ConfigKeys.EXECUTOR_STORAGE_MAX_THREAD_SIZE,
                    ConfigKeys.EXECUTOR_STORAGE_THREAD_QUEUE_CAPACITY));
        }
        if (dispatch != null) {
            entries.addAll(dispatch.toConfigEntries(
                    ConfigKeys.EXECUTOR_DISPATCH_THREAD_CORE_SIZE,
                    ConfigKeys.EXECUTOR_DISPATCH_MAX_THREAD_SIZE,
                    ConfigKeys.EXECUTOR_DISPATCH_THREAD_QUEUE_CAPACITY));
        }
        return entries;
    }
}
