package org.dbsyncer.parser.model;

import org.dbsyncer.sdk.constant.ConfigConstant;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户组实体类
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
public class UserGroup extends ConfigModel {

    public UserGroup() {
        super.setType(ConfigConstant.USER_GROUP);
    }


    /**
     * 用户组描述
     */
    private String description;

    /**
     * 用户ID列表（属于该组的用户）
     */
    private List<String> userIds = new ArrayList<>();

    /**
     * 任务分组ID列表（该组可访问的任务分组）
     */
    private List<String> projectGroupIds = new ArrayList<>();


    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public List<String> getProjectGroupIds() {
        return projectGroupIds;
    }

    public void setProjectGroupIds(List<String> projectGroupIds) {
        this.projectGroupIds = projectGroupIds;
    }
}
