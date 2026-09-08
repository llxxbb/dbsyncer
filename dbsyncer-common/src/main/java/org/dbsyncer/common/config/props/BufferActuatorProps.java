package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个执行器的缓冲区参数。
 * <p>
 * S1：全部包装类型。
 * <p>
 * 实现 {@link EnvironmentLoadable}：父 Props 创建实例后通过 {@link #setConfigKeys} 注入 key，
 * 再调用 {@link #loadFromEnvironment} 完成自加载。
 */
public class BufferActuatorProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private Integer bufferWriterCount = 100;
    private Integer bufferPullCount = 1000;
    private Integer bufferQueueCapacity = 30000;
    private Integer bufferPeriodMillisecond = 300;

    // 由父 Props 注入的配置键
    private String writerCountKey;
    private String pullCountKey;
    private String queueCapacityKey;
    private String periodMillisecondKey;

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
     * 注入配置键（由父 Props 在 loadFromEnvironment 前调用）。
     *
     * @param writerCountKey       写入线程数配置键（引用 {@link ConfigKeys}）
     * @param pullCountKey         每次消费任务数配置键
     * @param queueCapacityKey     队列容量配置键
     * @param periodMillisecondKey 消费间隔配置键
     */
    public void setConfigKeys(String writerCountKey, String pullCountKey, String queueCapacityKey, String periodMillisecondKey) {
        this.writerCountKey = writerCountKey;
        this.pullCountKey = pullCountKey;
        this.queueCapacityKey = queueCapacityKey;
        this.periodMillisecondKey = periodMillisecondKey;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        Integer wc = env.getProperty(writerCountKey, Integer.class);
        if (wc != null) this.bufferWriterCount = wc;
        Integer pc = env.getProperty(pullCountKey, Integer.class);
        if (pc != null) this.bufferPullCount = pc;
        Integer qc = env.getProperty(queueCapacityKey, Integer.class);
        if (qc != null) this.bufferQueueCapacity = qc;
        Integer pm = env.getProperty(periodMillisecondKey, Integer.class);
        if (pm != null) this.bufferPeriodMillisecond = pm;
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

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        // key 为 null 说明是从 DB 反序列化且未经 setConfigKeys 注入，跳过
        if (writerCountKey != null) entries.add(new ConfigEntry(writerCountKey, bufferWriterCount));
        if (pullCountKey != null) entries.add(new ConfigEntry(pullCountKey, bufferPullCount));
        if (queueCapacityKey != null) entries.add(new ConfigEntry(queueCapacityKey, bufferQueueCapacity));
        if (periodMillisecondKey != null) entries.add(new ConfigEntry(periodMillisecondKey, bufferPeriodMillisecond));
        return entries;
    }
}
