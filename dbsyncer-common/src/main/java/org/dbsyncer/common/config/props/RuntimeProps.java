package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 运行时控制配置（C 类：立即生效）
 */
public class RuntimeProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    /** 是否启用自重启 */
    private boolean selfRestartEnabled = false;

    public boolean getSelfRestartEnabled() { return selfRestartEnabled; }
    public void setSelfRestartEnabled(boolean selfRestartEnabled) { this.selfRestartEnabled = selfRestartEnabled; }

    public void loadFromEnvironment(Environment env) {
        this.selfRestartEnabled = env.getProperty(ConfigKeys.RUNTIME_SELF_RESTART_ENABLED, Boolean.class, selfRestartEnabled);
    }

    public List<String> validate() {
        return Collections.emptyList();
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.RUNTIME_SELF_RESTART_ENABLED, selfRestartEnabled));
        return entries;
    }
}
