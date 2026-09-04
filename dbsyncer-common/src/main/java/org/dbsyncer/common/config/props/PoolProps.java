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
     * @param prefix 配置键前缀（如 dbsyncer.parser.general）
     */
    public List<ConfigEntry> toConfigEntries(String prefix) {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(prefix + ".thread-core-size", threadCoreSize));
        entries.add(new ConfigEntry(prefix + ".max-thread-size", maxThreadSize));
        entries.add(new ConfigEntry(prefix + ".thread-queue-capacity", threadQueueCapacity));
        return entries;
    }
}
