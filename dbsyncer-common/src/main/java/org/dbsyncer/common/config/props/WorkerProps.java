package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

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
}
