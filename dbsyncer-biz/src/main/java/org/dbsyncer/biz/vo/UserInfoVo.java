package org.dbsyncer.biz.vo;

import org.dbsyncer.parser.model.UserInfo;

import java.util.List;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/11/17 0:14
 */
public class UserInfoVo extends UserInfo {

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 分组名称列表（用于前端显示）
     */
    private List<String> groupNames;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<String> getGroupNames() {
        return groupNames;
    }

    public void setGroupNames(List<String> groupNames) {
        this.groupNames = groupNames;
    }
}