package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个执行器的缓冲区参数，被 {@link BufferProps} 复用。
 * <p>
 * 纯数据容器：只持有缓冲区参数值，不持有配置键名。
 * 配置键名由父 {@link BufferProps} 在 {@code toConfigEntries()} 中管理，
 * 避免 key 作为实例字段导致反序列化后丢失的问题。
 */
public class BufferActuatorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private int bufferWriterCount = 100;
    private int bufferPullCount = 1000;
    private int bufferQueueCapacity = 30000;
    private int bufferPeriodMillisecond = 300;

    public int getBufferWriterCount() {
        return bufferWriterCount;
    }

    public void setBufferWriterCount(int bufferWriterCount) {
        this.bufferWriterCount = bufferWriterCount;
    }

    public int getBufferPullCount() {
        return bufferPullCount;
    }

    public void setBufferPullCount(int bufferPullCount) {
        this.bufferPullCount = bufferPullCount;
    }

    public int getBufferQueueCapacity() {
        return bufferQueueCapacity;
    }

    public void setBufferQueueCapacity(int bufferQueueCapacity) {
        this.bufferQueueCapacity = bufferQueueCapacity;
    }

    public int getBufferPeriodMillisecond() {
        return bufferPeriodMillisecond;
    }

    public void setBufferPeriodMillisecond(int bufferPeriodMillisecond) {
        this.bufferPeriodMillisecond = bufferPeriodMillisecond;
    }

    /**
     * 校验缓冲区参数，返回错误信息列表。
     *
     * @param name 缓冲区名称（用于错误提示）
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate(String name) {
        List<String> errors = new ArrayList<>();
        if (bufferQueueCapacity < 1) {
            errors.add(name + "队列容量必须 > 0");
        }
        if (bufferPeriodMillisecond < 1) {
            errors.add(name + "消费间隔必须 > 0 毫秒");
        }
        if (bufferPullCount < 1) {
            errors.add(name + "每次消费任务数必须 > 0");
        }
        return errors;
    }
}
