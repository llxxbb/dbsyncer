package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 3 缓冲区配置。
 * <p>
 * 负责缓冲区参数的环境加载和配置条目生成。
 * 配置键名集中管理于此，字段作为纯数据容器，
 * 避免 key 作为实例字段导致反序列化后丢失的问题。
 */
public class BufferProps implements Serializable, EnvironmentLoadable {

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

    @Override
    public void loadFromEnvironment(Environment env) {
        Integer wc = env.getProperty(ConfigKeys.BUFFER_GENERAL_WRITER_COUNT, Integer.class);
        if (wc != null) this.bufferWriterCount = wc;
        Integer pc = env.getProperty(ConfigKeys.BUFFER_GENERAL_PULL_COUNT, Integer.class);
        if (pc != null) this.bufferPullCount = pc;
        Integer qc = env.getProperty(ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY, Integer.class);
        if (qc != null) this.bufferQueueCapacity = qc;
        Integer pm = env.getProperty(ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND, Integer.class);
        if (pm != null) this.bufferPeriodMillisecond = pm;
    }

    /**
     * 校验缓冲区参数，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (bufferQueueCapacity < 1) {
            errors.add("缓冲区队列容量必须 > 0");
        }
        if (bufferPeriodMillisecond < 1) {
            errors.add("缓冲区消费间隔必须 > 0 毫秒");
        }
        if (bufferPullCount < 1) {
            errors.add("缓冲区每次消费任务数必须 > 0");
        }
        return errors;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.BUFFER_GENERAL_WRITER_COUNT, bufferWriterCount));
        entries.add(new ConfigEntry(ConfigKeys.BUFFER_GENERAL_PULL_COUNT, bufferPullCount));
        entries.add(new ConfigEntry(ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY, bufferQueueCapacity));
        entries.add(new ConfigEntry(ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND, bufferPeriodMillisecond));
        return entries;
    }
}
