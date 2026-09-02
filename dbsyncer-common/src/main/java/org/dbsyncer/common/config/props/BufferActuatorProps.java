package org.dbsyncer.common.config.props;

import java.io.Serializable;

/**
 * 单个执行器的缓冲区参数。
 * S1：全部包装类型。
 */
public class BufferActuatorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer bufferWriterCount;
    private Integer bufferPullCount;
    private Integer bufferQueueCapacity;
    private Integer bufferPeriodMillisecond;

    public Integer getBufferWriterCount() {
        return bufferWriterCount;
    }

    public void setBufferWriterCount(Integer bufferWriterCount) {
        this.bufferWriterCount = bufferWriterCount;
    }

    public Integer getBufferPullCount() {
        return bufferPullCount;
    }

    public void setBufferPullCount(Integer bufferPullCount) {
        this.bufferPullCount = bufferPullCount;
    }

    public Integer getBufferQueueCapacity() {
        return bufferQueueCapacity;
    }

    public void setBufferQueueCapacity(Integer bufferQueueCapacity) {
        this.bufferQueueCapacity = bufferQueueCapacity;
    }

    public Integer getBufferPeriodMillisecond() {
        return bufferPeriodMillisecond;
    }

    public void setBufferPeriodMillisecond(Integer bufferPeriodMillisecond) {
        this.bufferPeriodMillisecond = bufferPeriodMillisecond;
    }
}
