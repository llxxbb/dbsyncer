package org.dbsyncer.biz.vo;

import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.parser.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户组视图对象
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
public class UserGroupVo extends UserGroup {

    /**
     * 用户数量
     */
    private int userSize;

    /**
     * 任务分组数量
     */
    private int projectGroupSize;

    /**
     * 用户列表（用于前端展示）
     */
    private List<UserInfo> users = new ArrayList<>();

    /**
     * 任务分组列表（用于前端展示）
     */
    private List<ProjectGroup> projectGroups = new ArrayList<>();

    public int getUserSize() {
        return userSize;
    }

    public void setUserSize(int userSize) {
        this.userSize = userSize;
    }

    public int getProjectGroupSize() {
        return projectGroupSize;
    }

    public void setProjectGroupSize(int projectGroupSize) {
        this.projectGroupSize = projectGroupSize;
    }

    public List<UserInfo> getUsers() {
        return users;
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users;
    }

    public List<ProjectGroup> getProjectGroups() {
        return projectGroups;
    }

    public void setProjectGroups(List<ProjectGroup> projectGroups) {
        this.projectGroups = projectGroups;
    }
}
