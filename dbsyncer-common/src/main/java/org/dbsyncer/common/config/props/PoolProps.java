package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 线程池三元组，被多处复用。
 * <p>
 * S1：全部包装类型，避免 NON_DEFAULT 序列化歧义。
 * <p>
 * 实现 {@link EnvironmentLoadable}：父 Props 创建实例后通过 {@link #setConfigKeys} 注入 key，
 * 再调用 {@link #loadFromEnvironment} 完成自加载。
 */
public class PoolProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private Integer threadCoreSize;
    private Integer maxThreadSize;
    private Integer threadQueueCapacity;

    // 由父 Props 注入的配置键
    private String threadCoreSizeKey;
    private String maxThreadSizeKey;
    private String threadQueueCapacityKey;

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
     * 注入配置键（由父 Props 在 loadFromEnvironment 前调用）。
     *
     * @param threadCoreSizeKey     核心线程数配置键（引用 {@link ConfigKeys}）
     * @param maxThreadSizeKey      最大线程数配置键
     * @param threadQueueCapacityKey 队列容量配置键
     */
    public void setConfigKeys(String threadCoreSizeKey, String maxThreadSizeKey, String threadQueueCapacityKey) {
        this.threadCoreSizeKey = threadCoreSizeKey;
        this.maxThreadSizeKey = maxThreadSizeKey;
        this.threadQueueCapacityKey = threadQueueCapacityKey;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        this.threadCoreSize = env.getProperty(threadCoreSizeKey, Integer.class);
        this.maxThreadSize = env.getProperty(maxThreadSizeKey, Integer.class);
        this.threadQueueCapacity = env.getProperty(threadQueueCapacityKey, Integer.class);
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

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        // key 为 null 说明是从 DB 反序列化且未经 setConfigKeys 注入，跳过
        if (threadCoreSizeKey != null) entries.add(new ConfigEntry(threadCoreSizeKey, threadCoreSize));
        if (maxThreadSizeKey != null) entries.add(new ConfigEntry(maxThreadSizeKey, maxThreadSize));
        if (threadQueueCapacityKey != null) entries.add(new ConfigEntry(threadQueueCapacityKey, threadQueueCapacity));
        return entries;
    }
}
