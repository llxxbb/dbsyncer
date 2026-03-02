/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.UserGroupVo;
import org.dbsyncer.parser.model.UserGroup;

import java.util.List;
import java.util.Map;

/**
 * 用户组管理服务
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
public interface UserGroupService {

    /**
     * 添加用户组
     *
     * @param params
     */
    String add(Map<String, String> params) throws Exception;

    /**
     * 编辑用户组
     *
     * @param params
     */
    String edit(Map<String, String> params) throws Exception;

    /**
     * 删除用户组
     *
     * @param id
     */
    String remove(String id) throws Exception;

    /**
     * 获取用户组详情
     *
     * @param id
     * @return
     */
    UserGroupVo getUserGroup(String id);

    /**
     * 获取所有用户组
     *
     * @return
     */
    List<UserGroup> getUserGroupAll();

    /**
     * 获取用户的用户组列表
     *
     * @param username 用户名
     * @return 用户组列表
     */
    List<UserGroup> getUserGroupsByUser(String username);

    /**
     * 获取用户组关联的所有任务分组ID
     *
     * @param userGroupId 用户组ID
     * @return 任务分组ID列表
     */
    List<String> getProjectGroupIdsByUserGroup(String userGroupId);

    /**
     * 将用户添加到指定的用户组中（更新用户组的userIds）
     *
     * @param username 用户名
     * @param userGroupIds 用户组ID列表（逗号分隔）
     */
    void addUserToGroups(String username, String userGroupIds) throws Exception;

    /**
     * 从指定的用户组中移除用户（更新用户组的userIds）
     *
     * @param username 用户名
     * @param userGroupIds 用户组ID列表（逗号分隔）
     */
    void removeUserFromGroups(String username, String userGroupIds) throws Exception;
}
