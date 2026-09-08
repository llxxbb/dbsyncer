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
 * 创建子 {@link BufferActuatorProps} 后注入 configKeys，委托子级完成自加载。
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
        BufferActuatorProps generalBuffer = new BufferActuatorProps();
        generalBuffer.setConfigKeys(
                ConfigKeys.BUFFER_GENERAL_WRITER_COUNT,
                ConfigKeys.BUFFER_GENERAL_PULL_COUNT,
                ConfigKeys.BUFFER_GENERAL_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_GENERAL_PERIOD_MILLISECOND);
        generalBuffer.loadFromEnvironment(env);
        this.general = generalBuffer;

        BufferActuatorProps storageBuffer = new BufferActuatorProps();
        storageBuffer.setConfigKeys(
                ConfigKeys.BUFFER_STORAGE_WRITER_COUNT,
                ConfigKeys.BUFFER_STORAGE_PULL_COUNT,
                ConfigKeys.BUFFER_STORAGE_QUEUE_CAPACITY,
                ConfigKeys.BUFFER_STORAGE_PERIOD_MILLISECOND);
        storageBuffer.loadFromEnvironment(env);
        this.storage = storageBuffer;
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
        if (general != null) {
            entries.addAll(general.toConfigEntries());
        }
        if (storage != null) {
            entries.addAll(storage.toConfigEntries());
        }
        return entries;
    }
}
