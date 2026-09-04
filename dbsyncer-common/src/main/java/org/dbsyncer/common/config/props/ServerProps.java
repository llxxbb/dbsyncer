package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.dbsyncer.common.config.ConfigKeys;
import org.springframework.core.env.Environment;

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
     * 从 Environment 读取配置值（A 类配置，只读，不落库）。
     */
    public void loadFromEnvironment(Environment env) {
        // server.port
        String port = env.getProperty(ConfigKeys.SERVER_PORT);
        if (port != null) {
            try {
                this.port = Integer.parseInt(port);
            } catch (NumberFormatException ignored) {}
        }
        
        // server.ssl.*
        this.sslEnabled = env.getProperty(ConfigKeys.SERVER_SSL_ENABLED, Boolean.class);
        this.sslKeyStore = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE);
        this.sslKeyStorePassword = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE_PASSWORD);
        this.sslKeyStoreType = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE_TYPE);
        this.sslKeyAlias = env.getProperty(ConfigKeys.SERVER_SSL_KEY_ALIAS);
        
        // server.http.*
        this.httpPort = env.getProperty(ConfigKeys.SERVER_HTTP_PORT, Integer.class);
        this.httpRedirect = env.getProperty(ConfigKeys.SERVER_HTTP_REDIRECT, Boolean.class);
        
        // server.context-path
        this.contextPath = env.getProperty(ConfigKeys.SERVER_CONTEXT_PATH);
        
        // server.session-timeout
        this.sessionTimeout = env.getProperty(ConfigKeys.SERVER_SESSION_TIMEOUT, Integer.class);
    }

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
