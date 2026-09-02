package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * A 类：日志配置（只读，不落库）
 */

public class LoggingProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String levelRoot;

    public String getLevelRoot() { return levelRoot; }
    public void setLevelRoot(String levelRoot) { this.levelRoot = levelRoot; }
}
