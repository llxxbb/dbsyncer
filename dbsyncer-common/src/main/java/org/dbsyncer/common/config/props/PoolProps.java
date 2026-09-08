package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 校验线程池参数，返回错误信息列表。
     *
     * @param name 执行器名称（用于错误提示）
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate(String name) {
        List<String> errors = new ArrayList<>();
        if (threadCoreSize != null && threadCoreSize < 1) {
            errors.add(name + "核心线程数必须 > 0");
        }
        if (maxThreadSize != null && maxThreadSize < 1) {
            errors.add(name + "最大线程数必须 > 0");
        }
        if (threadCoreSize != null && maxThreadSize != null
                && threadCoreSize > maxThreadSize) {
            errors.add(name + "核心线程数不能大于最大线程数");
        }
        if (threadQueueCapacity != null && threadQueueCapacity < 1) {
            errors.add(name + "队列容量必须 > 0");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     *
     * @param threadCoreSizeKey     核心线程数配置键（引用 {@link org.dbsyncer.common.config.ConfigKeys}）
     * @param maxThreadSizeKey      最大线程数配置键
     * @param threadQueueCapacityKey 队列容量配置键
     */
    public List<ConfigEntry> toConfigEntries(String threadCoreSizeKey, String maxThreadSizeKey, String threadQueueCapacityKey) {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(threadCoreSizeKey, threadCoreSize));
        entries.add(new ConfigEntry(maxThreadSizeKey, maxThreadSize));
        entries.add(new ConfigEntry(threadQueueCapacityKey, threadQueueCapacity));
        return entries;
    }
}
