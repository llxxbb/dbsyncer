package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 2 执行器配置
 */
public class ExecutorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通用执行器 → ExecutorFactory */
    private PoolProps general;

    /** 存储执行器 → ExecutorFactory */
    private PoolProps storage;

    /** 调度任务执行器 → ExecutorFactory */
    private PoolProps dispatch;

    public PoolProps getGeneral() {
        return general;
    }

    public void setGeneral(PoolProps general) {
        this.general = general;
    }

    public PoolProps getStorage() {
        return storage;
    }

    public void setStorage(PoolProps storage) {
        this.storage = storage;
    }

    public PoolProps getDispatch() {
        return dispatch;
    }

    public void setDispatch(PoolProps dispatch) {
        this.dispatch = dispatch;
    }

    /**
     * 校验执行器配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (general != null) {
            errors.addAll(general.validate("通用执行器"));
        }
        if (storage != null) {
            errors.addAll(storage.validate("存储执行器"));
        }
        if (dispatch != null) {
            errors.addAll(dispatch.validate("调度任务执行器"));
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
        if (dispatch != null) {
            entries.addAll(dispatch.toConfigEntries("dbsyncer.common.dispatch"));
        }
        return entries;
    }
}
