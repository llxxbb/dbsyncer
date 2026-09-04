package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A 类：帮助文档配置（只读，不落库）
 */

public class HelpdocProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String path;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public void loadFromEnvironment(Environment env) {
        this.path = env.getProperty(ConfigKeys.HELPDOC_PATH, String.class);
    }

    public List<String> validate() {
        return Collections.emptyList();
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.HELPDOC_PATH, path));
        return entries;
    }
}
