package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个执行器的缓冲区参数。
 * S1：全部包装类型。
 */
public class BufferActuatorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer bufferWriterCount = 100;
    private Integer bufferPullCount = 1000;
    private Integer bufferQueueCapacity = 30000;
    private Integer bufferPeriodMillisecond = 300;

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

    /**
     * 校验缓冲区参数，返回错误信息列表。
     *
     * @param name 缓冲区名称（用于错误提示）
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate(String name) {
        List<String> errors = new ArrayList<>();
        if (bufferQueueCapacity != null && bufferQueueCapacity < 1) {
            errors.add(name + "队列容量必须 > 0");
        }
        if (bufferPeriodMillisecond != null && bufferPeriodMillisecond < 1) {
            errors.add(name + "消费间隔必须 > 0 毫秒");
        }
        if (bufferPullCount != null && bufferPullCount < 1) {
            errors.add(name + "每次消费任务数必须 > 0");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     *
     * @param writerCountKey       写入线程数配置键（引用 {@link org.dbsyncer.common.config.ConfigKeys}）
     * @param pullCountKey         每次消费任务数配置键
     * @param queueCapacityKey     队列容量配置键
     * @param periodMillisecondKey 消费间隔配置键
     */
    public List<ConfigEntry> toConfigEntries(String writerCountKey, String pullCountKey, String queueCapacityKey, String periodMillisecondKey) {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(writerCountKey, bufferWriterCount));
        entries.add(new ConfigEntry(pullCountKey, bufferPullCount));
        entries.add(new ConfigEntry(queueCapacityKey, bufferQueueCapacity));
        entries.add(new ConfigEntry(periodMillisecondKey, bufferPeriodMillisecond));
        return entries;
    }
}
