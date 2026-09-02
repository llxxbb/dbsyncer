package org.dbsyncer.common.config.props;

import java.io.Serializable;

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
}
