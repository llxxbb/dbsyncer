package org.dbsyncer.common.model;

import org.dbsyncer.common.constant.ConfigConstant;

/**
 * 配置模型基类。
 * <p>
 * 从 dbsyncer-sdk 迁移到 dbsyncer-common，使底层模块可直接访问。
 */
public class ConfigModel {

    private String id;

    /**
     * system/user/connector/mapping/tableGroup/meta/projectGroup
     *
     * @see ConfigConstant
     */
    private String type;

    private String name;

    private Long createTime;

    private Long updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public ConfigModel setType(String type) {
        this.type = type;
        return this;
    }

    public String getName() {
        return name;
    }

    public ConfigModel setName(String name) {
        this.name = name;
        return this;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public ConfigModel setCreateTime(Long createTime) {
        this.createTime = createTime;
        return this;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public ConfigModel setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
        return this;
    }
}
