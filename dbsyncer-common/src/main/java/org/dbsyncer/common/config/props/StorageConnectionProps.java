package org.dbsyncer.common.config.props;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * A 类：存储连接配置（只读，不落库）
 */

public class StorageConnectionProps implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private String mysqlUrl;
    private String mysqlUsername;
    private String mysqlPassword;
    private String mysqlDriverClassName;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMysqlUrl() { return mysqlUrl; }
    public void setMysqlUrl(String mysqlUrl) { this.mysqlUrl = mysqlUrl; }
    public String getMysqlUsername() { return mysqlUsername; }
    public void setMysqlUsername(String mysqlUsername) { this.mysqlUsername = mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public void setMysqlPassword(String mysqlPassword) { this.mysqlPassword = mysqlPassword; }
    public String getMysqlDriverClassName() { return mysqlDriverClassName; }
    public void setMysqlDriverClassName(String mysqlDriverClassName) { this.mysqlDriverClassName = mysqlDriverClassName; }
}
