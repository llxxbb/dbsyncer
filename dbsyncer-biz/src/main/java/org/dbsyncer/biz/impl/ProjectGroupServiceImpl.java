/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.ConnectorService;
import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.ProjectGroupService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.UserGroupService;
import org.dbsyncer.biz.checker.Checker;
import org.dbsyncer.biz.enums.UserRoleEnum;
import org.dbsyncer.biz.vo.MappingVo;
import org.dbsyncer.biz.vo.ProjectGroupVo;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.model.ConfigModel;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.parser.model.UserInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分组
 *
 * @author xinpeng.Fu
 * @version 1.0.0
 * @date 2022/6/9 17:09
 **/
@Service
public class ProjectGroupServiceImpl extends BaseServiceImpl implements ProjectGroupService {

    @Resource
    private ConnectorService connectorService;

    @Resource
    private MappingService mappingService;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private Checker projectGroupChecker;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private UserGroupService userGroupService;

    @Override
    public String add(Map<String, String> params) throws Exception {
        ConfigModel model = projectGroupChecker.checkAddConfigModel(params);
        log(LogType.ConnectorLog.INSERT, model);

        return profileComponent.addConfigModel(model);
    }

    @Override
    public String edit(Map<String, String> params) throws Exception {
        ConfigModel model = projectGroupChecker.checkEditConfigModel(params);
        log(LogType.ConnectorLog.UPDATE, model);

        return profileComponent.editConfigModel(model);
    }

    @Override
    public String remove(String id) throws Exception {
        ProjectGroup projectGroup = profileComponent.getProjectGroup(id);
        log(LogType.ConnectorLog.DELETE, projectGroup);
        Assert.notNull(projectGroup, "该分组已被删除");
        profileComponent.removeConfigModel(id);
        return "删除分组成功!";
    }

    @Override
    public ProjectGroupVo getProjectGroup(String id) {
        ProjectGroupVo vo = new ProjectGroupVo();
        List<Connector> connectors = connectorService.getConnectorAll();
        vo.setConnectorSize(CollectionUtils.isEmpty(connectors) ? 0 : connectors.size());

        if (StringUtil.isBlank(id)) {
            vo.setConnectors(connectors);
            vo.setMappings(mappingService.getMappingAll());
            return vo;
        }

        ProjectGroup projectGroup = profileComponent.getProjectGroup(id);
        Assert.notNull(projectGroup, "该分组已被删除");
        BeanUtils.copyProperties(projectGroup, vo);
        vo.setConnectors(Collections.EMPTY_LIST);
        vo.setMappings(Collections.EMPTY_LIST);

        // 过滤连接器
        List<String> connectorIds = projectGroup.getConnectorIds();
        if (!CollectionUtils.isEmpty(connectorIds)) {
            Set<String> connectorIdSet = new HashSet<>(connectorIds);
            if (!CollectionUtils.isEmpty(connectors)) {
                vo.setConnectors(connectors.stream()
                        .filter((connector -> connectorIdSet.contains(connector.getId())))
                        .collect(Collectors.toList())
                );
            }
        }

        // 过滤驱动
        List<String> mappingIds = projectGroup.getMappingIds();
        if (!CollectionUtils.isEmpty(mappingIds)) {
            Set<String> mappingIdSet = new HashSet<>(mappingIds);
            List<MappingVo> mappings = mappingService.getMappingAll();
            if (!CollectionUtils.isEmpty(mappings)) {
                vo.setMappings(mappings.stream()
                        .filter((mapping -> mappingIdSet.contains(mapping.getId())))
                        .collect(Collectors.toList())
                );
            }
        }
        return vo;
    }

    @Override
    public ProjectGroupVo getProjectGroupUnUsed() {
        List<ProjectGroup> projectGroupAll = profileComponent.getProjectGroupAll();
        List<String> connectorUsed = new ArrayList<>();
        List<String> mappingUsed = new ArrayList<>();
        if(!CollectionUtils.isEmpty(projectGroupAll)){
            for (ProjectGroup projectGroup : projectGroupAll){
                connectorUsed.addAll(projectGroup.getConnectorIds());
                mappingUsed.addAll(projectGroup.getMappingIds());
            }
        }
        ProjectGroupVo vo = new ProjectGroupVo();
        List<Connector> connectorAll = connectorService.getConnectorAll();
        List<MappingVo> mappingAll = mappingService.getMappingAll();
        //移除之前使用的连接、驱动
        connectorAll = connectorAll.stream().filter(connector -> !connectorUsed.contains(connector.getId())).collect(Collectors.toList());
        mappingAll = mappingAll.stream().filter(mapping -> !mappingUsed.contains(mapping.getId())).collect(Collectors.toList());

        vo.setConnectors(connectorAll);
        vo.setConnectorSize(CollectionUtils.isEmpty(connectorAll) ? 0 : connectorAll.size());
        vo.setMappings(mappingAll);
        return vo;
    }

    @Override
    public List<ProjectGroup> getProjectGroupAll() {
        return profileComponent.getProjectGroupAll();
    }

    @Override
    public List<ProjectGroup> getProjectGroupsByUser(String username) {
        // 获取所有分组
        List<ProjectGroup> allGroups = profileComponent.getProjectGroupAll();

        if (StringUtil.isBlank(username)) {
            return allGroups;
        }

        try {
            // 获取用户信息
            UserInfo userInfo = userConfigService.getUserInfo(username);
            if (null == userInfo) {
                return allGroups;
            }

            // 管理员返回所有分组
            if (UserRoleEnum.isAdmin(userInfo.getRoleCode())) {
                return allGroups;
            }

            // 普通用户，只通过用户组间接关联获取任务分组
            String userGroupIds = userInfo.getUserGroupIds();
            if (StringUtil.isBlank(userGroupIds)) {
                // 用户没有分配用户组，返回空列表
                return Collections.emptyList();
            }

            // 获取用户所属用户组关联的所有任务分组ID
            Set<String> accessibleProjectGroupIds = new HashSet<>();
            String[] userGroupIdArray = StringUtil.split(userGroupIds, StringUtil.COMMA);
            for (String userGroupId : userGroupIdArray) {
                if (StringUtil.isBlank(userGroupId)) {
                    continue;
                }
                UserGroup userGroup = userGroupService.getUserGroup(userGroupId.trim());
                if (userGroup != null && !CollectionUtils.isEmpty(userGroup.getProjectGroupIds())) {
                    accessibleProjectGroupIds.addAll(userGroup.getProjectGroupIds());
                }
            }

            if (accessibleProjectGroupIds.isEmpty()) {
                return Collections.emptyList();
            }

            // 过滤分组
            return allGroups.stream()
                    .filter(group -> accessibleProjectGroupIds.contains(group.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return allGroups;
        }
    }

}