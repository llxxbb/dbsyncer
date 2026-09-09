package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 运行时控制配置（C 类：立即生效）
 * <p>
 * ADR-0031：删除 selfRestartEnabled 字段，热重载始终可用，无需开关。
 */
public class RuntimeProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    public void loadFromEnvironment(Environment env) {
        // ADR-0031：无配置项，保留方法以兼容接口
    }

    @Override
    public List<String> validate() {
        return Collections.emptyList();
    }

    public List<ConfigEntry> toConfigEntries() {
        return new ArrayList<>();
    }
}
