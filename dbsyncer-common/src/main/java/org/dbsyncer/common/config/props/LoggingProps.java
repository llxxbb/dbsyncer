package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A 类：日志配置（只读，不落库）
 */

public class LoggingProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private String levelRoot;

    public String getLevelRoot() { return levelRoot; }
    public void setLevelRoot(String levelRoot) { this.levelRoot = levelRoot; }

    public void loadFromEnvironment(Environment env) {
        this.levelRoot = env.getProperty(ConfigKeys.LOGGING_LEVEL_ROOT, String.class, levelRoot);
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (levelRoot != null) {
            List<String> valid = Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF");
            if (!valid.contains(levelRoot.toUpperCase())) {
                errors.add("日志级别必须是: " + String.join(", ", valid));
            }
        }
        return errors;
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.LOGGING_LEVEL_ROOT, levelRoot));
        return entries;
    }
}
