package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 3 缓冲区配置
 */
public class BufferProps implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通用执行器缓冲 → GeneralBufferConfig */
    private BufferActuatorProps general;

    /** 存储执行器缓冲 → StorageConfig */
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

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        if (general != null) {
            entries.addAll(general.toConfigEntries("dbsyncer.parser.general"));
        }
        if (storage != null) {
            entries.addAll(storage.toConfigEntries("dbsyncer.storage"));
        }
        return entries;
    }
}
