package org.dbsyncer.common.config.props;

import java.io.Serializable;

/**
 * Tab 2 执行器配置
 */
public class ExecutorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通用执行器 → GeneralBufferConfig */
    private PoolProps general;

    /** 存储执行器 → StorageConfig */
    private PoolProps storage;

    /** 调度任务执行器 → DispatchTaskConfig */
    private PoolProps dispatch;

    /** 调度线程池 → SchedulerConfig.poolSize */
    private Integer schedulerPoolSize;

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

    public Integer getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(Integer schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }
}
