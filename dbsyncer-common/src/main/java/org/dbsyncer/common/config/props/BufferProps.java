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
 * 负责子 {@link BufferActuatorProps} 的创建、环境加载和配置条目生成。
 * 配置键名集中管理于此，子对象作为纯数据容器，
 * 避免 key 作为实例字段导致反序列化后丢失的问题。
 */
public class BufferProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    /** 通用执行器缓冲 → ExecutorFactory */
    private BufferActuatorProps general;

    /** 存储执行器缓冲 → ExecutorFactory */
    private BufferActuatorProps storage;

    public BufferActuatorProps getGeneral() {
        return general;
    }

    public void setGeneral(BufferActuatorProps general) {
        this.general = general;
    }

    public BufferActuatorProps getStorage() {
        return storage;
    }

    public void setStorage(BufferActuatorProps storage) {
        this.storage = storage;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        this.general = readBuffer(env,
                ConfigKeys.BUFFER_GENERAL_WRITER_COUNT,
                ConfigKeys.BUFFER_GENERAL_PULL_COUNT,
                ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND);

        this.storage = readBuffer(env,
                ConfigKeys.BUFFER_STORAGE_WRITER_COUNT,
                ConfigKeys.BUFFER_STORAGE_PULL_COUNT,
                ConfigKeys.BUFFER_STORAGE_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_STORAGE_PERIOD_MILLISECOND);
    }

    private BufferActuatorProps readBuffer(Environment env,
                                           String writerKey, String pullKey,
                                           String queueKey, String periodKey) {
        BufferActuatorProps props = new BufferActuatorProps();
        Integer wc = env.getProperty(writerKey, Integer.class);
        if (wc != null) props.setBufferWriterCount(wc);
        Integer pc = env.getProperty(pullKey, Integer.class);
        if (pc != null) props.setBufferPullCount(pc);
        Integer qc = env.getProperty(queueKey, Integer.class);
        if (qc != null) props.setBufferQueueCapacity(qc);
        Integer pm = env.getProperty(periodKey, Integer.class);
        if (pm != null) props.setBufferPeriodMillisecond(pm);
        return props;
    }

    /**
     * 校验缓冲区配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (general != null) {
            errors.addAll(general.validate("通用缓冲区"));
        }
        if (storage != null) {
            errors.addAll(storage.validate("存储缓冲区"));
        }
        return errors;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        addBufferEntries(entries, general,
                ConfigKeys.BUFFER_GENERAL_WRITER_COUNT,
                ConfigKeys.BUFFER_GENERAL_PULL_COUNT,
                ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND);
        addBufferEntries(entries, storage,
                ConfigKeys.BUFFER_STORAGE_WRITER_COUNT,
                ConfigKeys.BUFFER_STORAGE_PULL_COUNT,
                ConfigKeys.BUFFER_STORAGE_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_STORAGE_PERIOD_MILLISECOND);
        return entries;
    }

    private void addBufferEntries(List<ConfigEntry> entries, BufferActuatorProps props,
                                  String writerKey, String pullKey,
                                  String queueKey, String periodKey) {
        if (props == null) return;
        entries.add(new ConfigEntry(writerKey, props.getBufferWriterCount()));
        entries.add(new ConfigEntry(pullKey, props.getBufferPullCount()));
        entries.add(new ConfigEntry(queueKey, props.getBufferQueueCapacity()));
        entries.add(new ConfigEntry(periodKey, props.getBufferPeriodMillisecond()));
    }
}
