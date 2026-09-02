package org.dbsyncer.common.config.props;

import java.io.Serializable;

/**
 * 线程池三元组，被多处复用。
 * S1：全部包装类型，避免 NON_DEFAULT 序列化歧义。
 */
public class PoolProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer threadCoreSize;
    private Integer maxThreadSize;
    private Integer threadQueueCapacity;

    public Integer getThreadCoreSize() {
        return threadCoreSize;
    }

    public void setThreadCoreSize(Integer threadCoreSize) {
        this.threadCoreSize = threadCoreSize;
    }

    public Integer getMaxThreadSize() {
        return maxThreadSize;
    }

    public void setMaxThreadSize(Integer maxThreadSize) {
        this.maxThreadSize = maxThreadSize;
    }

    public Integer getThreadQueueCapacity() {
        return threadQueueCapacity;
    }

    public void setThreadQueueCapacity(Integer threadQueueCapacity) {
        this.threadQueueCapacity = threadQueueCapacity;
    }
}
