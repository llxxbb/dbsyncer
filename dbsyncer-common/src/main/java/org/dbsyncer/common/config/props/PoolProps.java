package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 线程池三元组，被 {@link ExecutorProps} 复用。
 * <p>
 * 纯数据容器：只持有线程池参数值，不持有配置键名。
 * 配置键名由父 {@link ExecutorProps} 在 {@code toConfigEntries()} 中管理，
 * 避免 key 作为实例字段导致反序列化后丢失的问题。
 * <p>
 * <b>例外（ADR-0029 实施修订 R1 / D2）：本类标量保持装箱。</b>
 * 每个执行器的默认值各不相同（含 {@code availableProcessors() * 2} 这类计算值），
 * 单个字段初值表达不了，仍由 {@code ExecutorFactory#getCore/getMax/getQueue} 按 null 兜底。
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
}
