package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

/**
 * A 类：服务器配置（只读，不落库）
 */

public class ServerProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private int port = 18686;
    private boolean sslEnabled = false;
    private String sslKeyStore;
    private String sslKeyStorePassword;
    private String sslKeyStoreType;
    private String sslKeyAlias;
    private int httpPort = 8080;
    private boolean httpRedirect = false;
    private String contextPath;
    private int sessionTimeout = 1800;

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean getSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
    public String getSslKeyStore() { return sslKeyStore; }
    public void setSslKeyStore(String sslKeyStore) { this.sslKeyStore = sslKeyStore; }
    public String getSslKeyStorePassword() { return sslKeyStorePassword; }
    public void setSslKeyStorePassword(String sslKeyStorePassword) { this.sslKeyStorePassword = sslKeyStorePassword; }
    public String getSslKeyStoreType() { return sslKeyStoreType; }
    public void setSslKeyStoreType(String sslKeyStoreType) { this.sslKeyStoreType = sslKeyStoreType; }
    public String getSslKeyAlias() { return sslKeyAlias; }
    public void setSslKeyAlias(String sslKeyAlias) { this.sslKeyAlias = sslKeyAlias; }
    public int getHttpPort() { return httpPort; }
    public void setHttpPort(int httpPort) { this.httpPort = httpPort; }
    public boolean getHttpRedirect() { return httpRedirect; }
    public void setHttpRedirect(boolean httpRedirect) { this.httpRedirect = httpRedirect; }
    public String getContextPath() { return contextPath; }
    public void setContextPath(String contextPath) { this.contextPath = contextPath; }
    public int getSessionTimeout() { return sessionTimeout; }
    public void setSessionTimeout(int sessionTimeout) { this.sessionTimeout = sessionTimeout; }

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
        this.sslEnabled = env.getProperty(ConfigKeys.SERVER_SSL_ENABLED, Boolean.class, sslEnabled);
        this.sslKeyStore = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE);
        this.sslKeyStorePassword = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE_PASSWORD);
        this.sslKeyStoreType = env.getProperty(ConfigKeys.SERVER_SSL_KEY_STORE_TYPE);
        this.sslKeyAlias = env.getProperty(ConfigKeys.SERVER_SSL_KEY_ALIAS);
        
        // server.http.*
        this.httpPort = env.getProperty(ConfigKeys.SERVER_HTTP_PORT, Integer.class, httpPort);
        this.httpRedirect = env.getProperty(ConfigKeys.SERVER_HTTP_REDIRECT, Boolean.class, httpRedirect);
        
        // server.context-path
        this.contextPath = env.getProperty(ConfigKeys.SERVER_CONTEXT_PATH);
        
        // server.session-timeout
        this.sessionTimeout = env.getProperty(ConfigKeys.SERVER_SESSION_TIMEOUT, Integer.class, sessionTimeout);
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if ((port < 1 || port > 65535)) {
            errors.add("服务器端口必须在 1-65535 之间");
        }
        if ((httpPort < 1 || httpPort > 65535)) {
            errors.add("HTTP 端口必须在 1-65535 之间");
        }
        if (sessionTimeout < 1) {
            errors.add("会话超时时间必须 >= 1 秒");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.SERVER_PORT, port));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SSL_ENABLED, sslEnabled));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SSL_KEY_STORE, sslKeyStore));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SSL_KEY_STORE_PASSWORD, sslKeyStorePassword, true));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SSL_KEY_STORE_TYPE, sslKeyStoreType));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SSL_KEY_ALIAS, sslKeyAlias));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_HTTP_PORT, httpPort));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_HTTP_REDIRECT, httpRedirect));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_CONTEXT_PATH, contextPath));
        entries.add(new ConfigEntry(ConfigKeys.SERVER_SESSION_TIMEOUT, sessionTimeout));
        return entries;
    }
}
