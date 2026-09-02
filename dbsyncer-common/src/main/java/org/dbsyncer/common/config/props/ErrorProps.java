package org.dbsyncer.common.config.props;

import org.dbsyncer.common.util.MatchMode;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

/**
 * Tab 6 错误处理（字段对齐 ErrorQueueConfig）。
 * S1：全部包装类型。
 */
public class ErrorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer threshold;
    private MatchMode fatalMatchMode;
    private List<String> fatalKeywords;

    public Integer getThreshold() {
        return threshold;
    }

    public void setThreshold(Integer threshold) {
        this.threshold = threshold;
    }

    public MatchMode getFatalMatchMode() {
        return fatalMatchMode;
    }

    public void setFatalMatchMode(MatchMode fatalMatchMode) {
        this.fatalMatchMode = fatalMatchMode;
    }

    public List<String> getFatalKeywords() {
        return fatalKeywords;
    }

    public void setFatalKeywords(List<String> fatalKeywords) {
        this.fatalKeywords = fatalKeywords;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("dbsyncer.error.threshold", threshold));
        entries.add(new ConfigEntry("dbsyncer.error.fatal-match-mode", fatalMatchMode));
        entries.add(new ConfigEntry("dbsyncer.error.fatal-keywords", fatalKeywords));
        return entries;
    }
}
