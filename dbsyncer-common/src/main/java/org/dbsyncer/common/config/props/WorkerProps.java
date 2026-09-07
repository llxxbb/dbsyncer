package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A 类：Worker 配置（只读，不落库）
 */

public class WorkerProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long dataCenterId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDataCenterId() { return dataCenterId; }
    public void setDataCenterId(Long dataCenterId) { this.dataCenterId = dataCenterId; }

    public void loadFromEnvironment(Environment env) {
        this.id = env.getProperty(ConfigKeys.WORKER_ID, Long.class);
        this.dataCenterId = env.getProperty(ConfigKeys.WORKER_DATA_CENTER_ID, Long.class);
        if (this.id == null) {
            this.id = 1L; // 默认值
        }
        if (this.dataCenterId == null) {
            this.dataCenterId = 1L; // 默认值
        }
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (id != null && (id < 0 || id > 31)) {
            errors.add("Worker ID 必须在 0-31 之间");
        }
        if (dataCenterId != null && (dataCenterId < 0 || dataCenterId > 31)) {
            errors.add("Data Center ID 必须在 0-31 之间");
        }
        return errors;
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.WORKER_ID, id));
        entries.add(new ConfigEntry(ConfigKeys.WORKER_DATA_CENTER_ID, dataCenterId));
        return entries;
    }
}
