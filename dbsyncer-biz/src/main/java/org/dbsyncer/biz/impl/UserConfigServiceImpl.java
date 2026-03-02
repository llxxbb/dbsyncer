/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.ProjectGroupService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.UserGroupService;
import org.dbsyncer.biz.checker.impl.user.UserConfigChecker;
import org.dbsyncer.biz.enums.UserRoleEnum;
import org.dbsyncer.biz.vo.UserInfoVo;
import org.dbsyncer.common.util.SHA1Util;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.parser.model.UserInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/11/17 0:16
 */
@Service
public class UserConfigServiceImpl implements UserConfigService {

    private static final String DEFAULT_USERNAME = "admin";

    private static final String DEFAULT_PASSWORD = "0DPiKuNIrrVmD8IUCuw1hQxNqZc=";

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private UserConfigChecker userConfigChecker;

    @Resource
    private LogService logService;

    @Resource
    private ProjectGroupService projectGroupService;

    @Resource
    private UserGroupService userGroupService;

    @Override
    public synchronized String add(Map<String, String> params) throws Exception {
        String username = params.get("username");
        Assert.hasText(username, "The username is null.");
        String nickname = params.get("nickname");
        Assert.hasText(nickname, "The nickname is null.");
        String password = params.get("password");
        Assert.hasText(password, "The password is null.");
        String email = params.get("email");
        String phone = params.get("phone");
        String groupIds = params.get("groupIds");
        String userGroupIds = params.get("userGroupIds");

        // 验证当前登录用户合法身份（必须是管理员操作）
        UserConfig userConfig = getUserConfig();
        UserInfo currentUser = userConfig.getUserInfo(params.get(UserConfigService.CURRENT_USER_NAME));
        Assert.isTrue(null == currentUser || UserRoleEnum.isAdmin(currentUser.getRoleCode()), "No permission.");
        // 新用户合法性（用户不能重复）
        Assert.isNull(userConfig.getUserInfo(username), "用户已存在，请换个账号");
        // 注册新用户
        userConfig.getUserInfoList().add(new UserInfo(username, nickname, SHA1Util.b64_sha1(password), UserRoleEnum.USER.getCode(), email, phone, groupIds, userGroupIds));

        // 保存用户配置
        String result = profileComponent.editConfigModel(userConfig);

        // 维护用户组的双向关联：将用户添加到指定的用户组中
        userGroupService.addUserToGroups(username, userGroupIds);

        logService.log(LogType.UserLog.INSERT, String.format("[%s]添加[%s]账号成功", currentUser.getUsername(), username));
        return result;
    }

    @Override
    public synchronized String edit(Map<String, String> params) throws Exception {
        String username = params.get("username");
        Assert.hasText(username, "The username is null.");
        String nickname = params.get("nickname");
        Assert.hasText(nickname, "The nickname is null.");
        String newPwd = params.get("newPwd");
        String email = params.get("email");
        String phone = params.get("phone");
        String newUserGroupIds = params.get("userGroupIds");

        // 验证当前登录用户合法身份（管理员或本人操作）
        UserConfig userConfig = getUserConfig();
        UserInfo currentUser = userConfig.getUserInfo(params.get(UserConfigService.CURRENT_USER_NAME));
        boolean admin = null != currentUser && UserRoleEnum.isAdmin(currentUser.getRoleCode());
        boolean self = null != currentUser && StringUtil.equals(currentUser.getUsername(), username);
        Assert.isTrue(admin || self, "No permission.");

        // 修改自己或其他用户信息
        UserInfo updateUser = self ? currentUser : userConfig.getUserInfo(username);
        Assert.notNull(updateUser, "用户不存在");

        // 获取旧的用户组ID，用于后续维护双向关联
        String oldUserGroupIds = updateUser.getUserGroupIds();

        // 用户昵称
        updateUser.setNickname(nickname);
        updateUser.setEmail(email);
        updateUser.setPhone(phone);
        updateUser.setGroupIds(params.get("groupIds"));
        updateUser.setUserGroupIds(newUserGroupIds);
        // 修改密码
        if (StringUtil.isNotBlank(newPwd)) {
            // 修改自己的密码需要验证
            if (self) {
                String oldPwd = params.get("oldPwd");
                Assert.hasText(oldPwd, "旧密码不能为空.");
                if (!StringUtil.equals(SHA1Util.b64_sha1(oldPwd), updateUser.getPassword())) {
                    logService.log(LogType.SystemLog.ERROR, String.format("[%s]修改密码失败", username));
                    throw new BizException("修改密码失败.");
                }
            }
            newPwd = SHA1Util.b64_sha1(newPwd);
            Assert.isTrue(!StringUtil.equals(newPwd, updateUser.getPassword()), "新旧密码不能完全一样.");
            updateUser.setPassword(newPwd);
            logService.log(LogType.UserLog.UPDATE, String.format("[%s]修改[%s]账号密码成功", currentUser.getUsername(), username));
        }

        // 保存用户配置
        String result = profileComponent.editConfigModel(userConfig);

        // 维护用户组的双向关联
        // 1. 从旧的用户组中移除该用户
        if (StringUtil.isNotBlank(oldUserGroupIds)) {
            userGroupService.removeUserFromGroups(username, oldUserGroupIds);
        }
        // 2. 将用户添加到新的用户组中
        if (StringUtil.isNotBlank(newUserGroupIds)) {
            userGroupService.addUserToGroups(username, newUserGroupIds);
        }

        return result;
    }

    @Override
    public synchronized String remove(Map<String, String> params) throws Exception {
        String username = params.get("username");
        Assert.hasText(username, "The username is null.");

        // 验证当前登录用户合法身份（必须是管理员操作）
        UserConfig userConfig = getUserConfig();
        UserInfo currentUser = userConfig.getUserInfo(params.get(UserConfigService.CURRENT_USER_NAME));
        Assert.isTrue(UserRoleEnum.isAdmin(currentUser.getRoleCode()), "No permission.");

        // 不能删除自己
        Assert.isTrue(!StringUtil.equals(currentUser.getUsername(), username), "不能删除自己.");

        // 删除用户
        UserInfo deleteUser = userConfig.getUserInfo(username);
        Assert.notNull(deleteUser, "用户已删除.");
        
        // 获取用户的用户组ID，用于后续从用户组中移除
        String userGroupIds = deleteUser.getUserGroupIds();
        
        userConfig.removeUserInfo(username);
        profileComponent.editConfigModel(userConfig);
        
        // 从用户组中移除该用户
        if (StringUtil.isNotBlank(userGroupIds)) {
            userGroupService.removeUserFromGroups(username, userGroupIds);
        }
        
        logService.log(LogType.UserLog.DELETE, String.format("[%s]删除[%s]账号成功", currentUser.getUsername(), username));
        return "删除用户成功!";
    }

    @Override
    public UserInfo getUserInfo(String currentUserName) throws Exception {
        return getUserConfig().getUserInfo(currentUserName);
    }

    @Override
    public UserInfoVo getUserInfoVo(String currentUserName, String username) throws Exception {
        // 管理员可以查看所有用户，普通用户只能查看自己
        UserConfig userConfig = getUserConfig();
        UserInfo currentUser = userConfig.getUserInfo(currentUserName);
        boolean admin = null != currentUser && UserRoleEnum.isAdmin(currentUser.getRoleCode());
        boolean self = null != currentUser && StringUtil.equals(currentUser.getUsername(), username);
        Assert.isTrue(admin || self, "No permission.");

        UserInfo userInfo = getUserConfig().getUserInfo(username);
        return convertUserInfo2Vo(userInfo);
    }

    @Override
    public List<UserInfoVo> getUserInfoAll(String currentUserName) throws Exception {
        // 系统管理员可以查看所有用户，其他用户只能查看自己
        UserConfig userConfig = getUserConfig();
        UserInfo currentUser = userConfig.getUserInfo(currentUserName);
        boolean admin = null != currentUser && UserRoleEnum.isAdmin(currentUser.getRoleCode());
        if (admin) {
            return getUserConfig().getUserInfoList().stream().map(user -> convertUserInfo2Vo(user)).collect(Collectors.toList());
        }

        List<UserInfoVo> list = new ArrayList<>();
        UserInfo userInfo = userConfig.getUserInfo(currentUserName);
        list.add(convertUserInfo2Vo(userInfo));
        return list;
    }

    @Override
    public UserConfig getUserConfig() throws Exception {
        UserConfig config = profileComponent.getUserConfig();
        if (null != config) {
            return config;
        }

        synchronized (this) {
            config = profileComponent.getUserConfig();
            if (null == config) {
                config = (UserConfig) userConfigChecker.checkAddConfigModel(new HashMap<>());
                config.getUserInfoList().add(getDefaultUser());
                profileComponent.addConfigModel(config);
            }
            return config;
        }
    }

    @Override
    public UserInfo getDefaultUser() {
        return new UserInfo(DEFAULT_USERNAME, DEFAULT_USERNAME, DEFAULT_PASSWORD, UserRoleEnum.ADMIN.getCode(), StringUtil.EMPTY, StringUtil.EMPTY, StringUtil.EMPTY);
    }

    private UserInfoVo convertUserInfo2Vo(UserInfo userInfo) {
        UserInfoVo userInfoVo = new UserInfoVo();
        if (null != userInfo) {
            BeanUtils.copyProperties(userInfo, userInfoVo);
            // 避免密码直接暴露
            userInfoVo.setPassword("***");
            userInfoVo.setRoleName(UserRoleEnum.getNameByCode(userInfo.getRoleCode()));

            // 转换用户组ID为用户组名称列表（用于前端展示）
            if (StringUtil.isNotBlank(userInfo.getUserGroupIds())) {
                String[] userGroupIdArray = StringUtil.split(userInfo.getUserGroupIds(), StringUtil.COMMA);
                List<String> userGroupNames = new ArrayList<>();
                List<UserGroup> allUserGroups = userGroupService.getUserGroupAll();
                for (String userGroupId : userGroupIdArray) {
                    allUserGroups.stream()
                        .filter(ug -> ug.getId().equals(userGroupId.trim()))
                        .findFirst()
                        .ifPresent(ug -> userGroupNames.add(ug.getName()));
                }
                userInfoVo.setUserGroupNames(userGroupNames);
            }
        }
        return userInfoVo;
    }

    @Override
    public List<ProjectGroup> getProjectGroupAll() {
        return projectGroupService.getProjectGroupAll();
    }

}