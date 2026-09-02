package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A 类：服务器配置（只读，不落库）
 */

public class ServerProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer port;
    private Boolean sslEnabled;
    private String sslKeyStore;
    private String sslKeyStorePassword;
    private String sslKeyStoreType;
    private String sslKeyAlias;
    private Integer httpPort;
    private Boolean httpRedirect;
    private String contextPath;
    private Integer sessionTimeout;

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public Boolean getSslEnabled() { return sslEnabled; }
    public void setSslEnabled(Boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public String getSslKeyStore() { return sslKeyStore; }
    public void setSslKeyStore(String sslKeyStore) { this.sslKeyStore = sslKeyStore; }
    public String getSslKeyStorePassword() { return sslKeyStorePassword; }
    public void setSslKeyStorePassword(String sslKeyStorePassword) { this.sslKeyStorePassword = sslKeyStorePassword; }
    public String getSslKeyStoreType() { return sslKeyStoreType; }
    public void setSslKeyStoreType(String sslKeyStoreType) { this.sslKeyStoreType = sslKeyStoreType; }
    public String getSslKeyAlias() { return sslKeyAlias; }
    public void setSslKeyAlias(String sslKeyAlias) { this.sslKeyAlias = sslKeyAlias; }
    public Integer getHttpPort() { return httpPort; }
    public void setHttpPort(Integer httpPort) { this.httpPort = httpPort; }
    public Boolean getHttpRedirect() { return httpRedirect; }
    public void setHttpRedirect(Boolean httpRedirect) { this.httpRedirect = httpRedirect; }
    public String getContextPath() { return contextPath; }
    public void setContextPath(String contextPath) { this.contextPath = contextPath; }
    public Integer getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(Integer sessionTimeout) { this.sessionTimeout = sessionTimeout; }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("server.port", port));
        entries.add(new ConfigEntry("server.ssl.enabled", sslEnabled));
        entries.add(new ConfigEntry("server.ssl.key-store", sslKeyStore));
        entries.add(new ConfigEntry("server.ssl.key-store-password", sslKeyStorePassword, true));
        entries.add(new ConfigEntry("server.ssl.key-store-type", sslKeyStoreType));
        entries.add(new ConfigEntry("server.ssl.key-alias", sslKeyAlias));
        entries.add(new ConfigEntry("server.http.port", httpPort));
        entries.add(new ConfigEntry("server.http.redirect", httpRedirect));
        entries.add(new ConfigEntry("server.context-path", contextPath));
        entries.add(new ConfigEntry("server.session-timeout", sessionTimeout));
        return entries;
    }
}
