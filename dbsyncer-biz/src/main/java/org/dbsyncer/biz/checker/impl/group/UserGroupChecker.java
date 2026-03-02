/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.checker.impl.group;

import org.dbsyncer.biz.checker.AbstractChecker;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.ConfigModel;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * 用户组校验器
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
@Component
public class UserGroupChecker extends AbstractChecker {

    @Resource
    private ProfileComponent profileComponent;

    /**
     * 新增配置
     *
     * @param params
     * @return
     */
    @Override
    public ConfigModel checkAddConfigModel(Map<String, String> params) {
        String name = params.get(ConfigConstant.CONFIG_MODEL_NAME);
        UserGroup userGroup = new UserGroup();
        userGroup.setName(name);

        modifyUserGroup(userGroup, params);

        // 修改基本配置
        this.modifyConfigModel(userGroup, params);

        return userGroup;
    }

    /**
     * 修改配置
     *
     * @param params
     * @return
     */
    @Override
    public ConfigModel checkEditConfigModel(Map<String, String> params) {
        String id = params.get(ConfigConstant.CONFIG_MODEL_ID);
        UserGroup userGroup = profileComponent.getUserGroup(id);
        Assert.notNull(userGroup, "Can not find user group.");

        modifyUserGroup(userGroup, params);

        // 修改基本配置
        this.modifyConfigModel(userGroup, params);
        return userGroup;
    }

    private void modifyUserGroup(UserGroup userGroup, Map<String, String> params) {
        // 描述
        String description = params.get("description");
        userGroup.setDescription(description);

        // 用户ID列表
        String[] userIds = StringUtil.split(params.get("userIds"), StringUtil.COMMA);
        userGroup.setUserIds(CollectionUtils.isEmpty(userIds) ? Collections.EMPTY_LIST : Arrays.asList(userIds));

        // 任务分组ID列表
        String[] projectGroupIds = StringUtil.split(params.get("projectGroupIds"), StringUtil.COMMA);
        userGroup.setProjectGroupIds(CollectionUtils.isEmpty(projectGroupIds) ? Collections.EMPTY_LIST : Arrays.asList(projectGroupIds));
    }

}
