package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * A 类：License 配置（只读，不落库，脱敏）
 */

public class LicenseProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverAddress;

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }

    public void loadFromEnvironment(Environment env) {
        this.serverAddress = env.getProperty(ConfigKeys.LICENSE_SERVER_ADDRESS, String.class);
    }

    public List<String> validate() {
        return Collections.emptyList();
    }
}
