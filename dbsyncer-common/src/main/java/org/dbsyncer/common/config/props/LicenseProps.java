package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * A 类：License 配置（只读，不落库，脱敏）
 */

public class LicenseProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverAddress;

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }
}
