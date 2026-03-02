/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.UserGroupService;
import org.dbsyncer.biz.checker.Checker;
import org.dbsyncer.biz.vo.UserGroupVo;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.model.ConfigModel;
import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.parser.model.UserInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户组服务实现
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
@Service
public class UserGroupServiceImpl extends BaseServiceImpl implements UserGroupService {

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private Checker userGroupChecker;

    @Resource
    private UserConfigService userConfigService;

    @Override
    public String add(Map<String, String> params) throws Exception {
        ConfigModel model = userGroupChecker.checkAddConfigModel(params);
        log(LogType.UserLog.INSERT, model);

        String result = profileComponent.addConfigModel(model);

        // 更新关联用户的userGroupIds
        updateUserUserGroupIds((UserGroup) model);

        return result;
    }

    @Override
    public String edit(Map<String, String> params) throws Exception {
        String id = params.get("id");
        UserGroup oldUserGroup = profileComponent.getUserGroup(id);

        // 保存旧的用户列表，用于后续移除关联
        // 注意：必须在checkEditConfigModel之前保存，因为checkEditConfigModel会修改缓存中的对象
        List<String> oldUserIds = oldUserGroup != null && oldUserGroup.getUserIds() != null
                ? new ArrayList<>(oldUserGroup.getUserIds())
                : null;

        ConfigModel model = userGroupChecker.checkEditConfigModel(params);
        log(LogType.UserLog.UPDATE, model);

        String result = profileComponent.editConfigModel(model);

        // 更新关联用户的userGroupIds（先移除旧关联，再添加新关联）
        if (oldUserIds != null) {
            // 使用保存的旧用户列表创建临时UserGroup对象，用于移除关联
            UserGroup tempOldUserGroup = new UserGroup();
            tempOldUserGroup.setId(id);
            tempOldUserGroup.setUserIds(oldUserIds);
            removeUserUserGroupIds(tempOldUserGroup);
        }
        updateUserUserGroupIds((UserGroup) model);

        return result;
    }

    @Override
    public String remove(String id) throws Exception {
        UserGroup userGroup = profileComponent.getUserGroup(id);
        Assert.notNull(userGroup, "该用户组已被删除");

        log(LogType.UserLog.DELETE, userGroup);

        // 先移除关联用户的userGroupIds
        removeUserUserGroupIds(userGroup);

        profileComponent.removeConfigModel(id);
        return "删除用户组成功!";
    }

    @Override
    public UserGroupVo getUserGroup(String id) {
        UserGroupVo vo = new UserGroupVo();

        if (StringUtil.isBlank(id)) {
            return vo;
        }

        UserGroup userGroup = profileComponent.getUserGroup(id);
        Assert.notNull(userGroup, "该用户组已被删除");
        BeanUtils.copyProperties(userGroup, vo);

        // 设置用户列表
        List<String> userIds = userGroup.getUserIds();
        if (!CollectionUtils.isEmpty(userIds)) {
            vo.setUserSize(userIds.size());
            try {
                UserConfig userConfig = userConfigService.getUserConfig();
                List<UserInfo> users = userIds.stream()
                        .map(userConfig::getUserInfo)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                vo.setUsers(users);
            } catch (Exception e) {
                // 忽略异常
            }
        }

        // 设置任务分组列表
        List<String> projectGroupIds = userGroup.getProjectGroupIds();
        if (!CollectionUtils.isEmpty(projectGroupIds)) {
            vo.setProjectGroupSize(projectGroupIds.size());
            List<ProjectGroup> allProjectGroups = profileComponent.getProjectGroupAll();
            Set<String> projectGroupIdSet = new HashSet<>(projectGroupIds);
            List<ProjectGroup> projectGroups = allProjectGroups.stream()
                    .filter(pg -> projectGroupIdSet.contains(pg.getId()))
                    .collect(Collectors.toList());
            vo.setProjectGroups(projectGroups);
        }

        return vo;
    }

    @Override
    public List<UserGroup> getUserGroupAll() {
        return profileComponent.getUserGroupAll();
    }

    @Override
    public List<UserGroup> getUserGroupsByUser(String username) {
        if (StringUtil.isBlank(username)) {
            return Collections.emptyList();
        }

        try {
            UserInfo userInfo = userConfigService.getUserInfo(username);
            if (userInfo == null || StringUtil.isBlank(userInfo.getUserGroupIds())) {
                return Collections.emptyList();
            }

            String[] userGroupIds = StringUtil.split(userInfo.getUserGroupIds(), StringUtil.COMMA);
            if (userGroupIds == null || userGroupIds.length == 0) {
                return Collections.emptyList();
            }

            Set<String> userGroupIdSet = Arrays.stream(userGroupIds)
                    .filter(StringUtil::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            List<UserGroup> allUserGroups = getUserGroupAll();
            return allUserGroups.stream()
                    .filter(ug -> userGroupIdSet.contains(ug.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getProjectGroupIdsByUserGroup(String userGroupId) {
        if (StringUtil.isBlank(userGroupId)) {
            return Collections.emptyList();
        }

        UserGroup userGroup = profileComponent.getUserGroup(userGroupId);
        if (userGroup == null || CollectionUtils.isEmpty(userGroup.getProjectGroupIds())) {
            return Collections.emptyList();
        }

        return new ArrayList<>(userGroup.getProjectGroupIds());
    }

    @Override
    public void addUserToGroups(String username, String userGroupIds) throws Exception {
        if (StringUtil.isBlank(username) || StringUtil.isBlank(userGroupIds)) {
            return;
        }

        String[] groupIds = StringUtil.split(userGroupIds, StringUtil.COMMA);
        for (String groupId : groupIds) {
            if (StringUtil.isBlank(groupId)) {
                continue;
            }
            UserGroup userGroup = profileComponent.getUserGroup(groupId.trim());
            if (userGroup == null) {
                continue;
            }

            // 添加用户到用户组
            List<String> userIds = userGroup.getUserIds();
            if (userIds == null) {
                userIds = new ArrayList<>();
                userGroup.setUserIds(userIds);
            }
            if (!userIds.contains(username)) {
                userIds.add(username);
                profileComponent.editConfigModel(userGroup);
            }
        }
    }

    @Override
    public void removeUserFromGroups(String username, String userGroupIds) throws Exception {
        if (StringUtil.isBlank(username) || StringUtil.isBlank(userGroupIds)) {
            return;
        }

        String[] groupIds = StringUtil.split(userGroupIds, StringUtil.COMMA);
        for (String groupId : groupIds) {
            if (StringUtil.isBlank(groupId)) {
                continue;
            }
            UserGroup userGroup = profileComponent.getUserGroup(groupId.trim());
            if (userGroup == null || CollectionUtils.isEmpty(userGroup.getUserIds())) {
                continue;
            }

            // 从用户组中移除用户
            if (userGroup.getUserIds().remove(username)) {
                profileComponent.editConfigModel(userGroup);
            }
        }
    }

    /**
     * 更新关联用户的userGroupIds字段
     */
    private void updateUserUserGroupIds(UserGroup userGroup) throws Exception {
        if (userGroup == null || CollectionUtils.isEmpty(userGroup.getUserIds())) {
            return;
        }

        UserConfig userConfig = userConfigService.getUserConfig();
        String userGroupId = userGroup.getId();

        for (String username : userGroup.getUserIds()) {
            UserInfo userInfo = userConfig.getUserInfo(username);
            if (userInfo == null) {
                continue;
            }

            String userGroupIds = userInfo.getUserGroupIds();
            Set<String> userGroupIdSet = new HashSet<>();

            if (StringUtil.isNotBlank(userGroupIds)) {
                String[] ids = StringUtil.split(userGroupIds, StringUtil.COMMA);
                for (String id : ids) {
                    if (StringUtil.isNotBlank(id)) {
                        userGroupIdSet.add(id.trim());
                    }
                }
            }

            // 添加新的用户组ID
            userGroupIdSet.add(userGroupId);

            // 更新用户
            userInfo.setUserGroupIds(String.join(StringUtil.COMMA, userGroupIdSet));
        }

        // 保存用户配置
        profileComponent.editConfigModel(userConfig);
    }

    /**
     * 移除关联用户的userGroupIds字段中的指定用户组ID
     */
    private void removeUserUserGroupIds(UserGroup userGroup) throws Exception {
        if (userGroup == null || CollectionUtils.isEmpty(userGroup.getUserIds())) {
            return;
        }

        UserConfig userConfig = userConfigService.getUserConfig();
        String userGroupId = userGroup.getId();

        for (String username : userGroup.getUserIds()) {
            UserInfo userInfo = userConfig.getUserInfo(username);
            if (userInfo == null || StringUtil.isBlank(userInfo.getUserGroupIds())) {
                continue;
            }

            String userGroupIds = userInfo.getUserGroupIds();
            String[] ids = StringUtil.split(userGroupIds, StringUtil.COMMA);
            Set<String> userGroupIdSet = new HashSet<>();

            for (String id : ids) {
                if (StringUtil.isNotBlank(id) && !userGroupId.equals(id.trim())) {
                    userGroupIdSet.add(id.trim());
                }
            }

            // 更新用户
            if (userGroupIdSet.isEmpty()) {
                userInfo.setUserGroupIds(null);
            } else {
                userInfo.setUserGroupIds(String.join(StringUtil.COMMA, userGroupIdSet));
            }
        }

        // 保存用户配置
        profileComponent.editConfigModel(userConfig);
    }
}
