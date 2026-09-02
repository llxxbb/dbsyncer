package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * A 类：帮助文档配置（只读，不落库）
 */

public class HelpdocProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String path;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
