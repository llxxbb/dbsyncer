package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.PermissionService;
import org.dbsyncer.biz.ProjectGroupService;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.UserGroupService;
import org.dbsyncer.biz.enums.UserRoleEnum;
import org.dbsyncer.biz.vo.MappingVo;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.UserGroup;
import org.dbsyncer.parser.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private ProjectGroupService projectGroupService;

    @Resource
    private UserGroupService userGroupService;

    @Resource
    private MappingService mappingService;

    @Resource
    private TableGroupService tableGroupService;

    @Override
    public boolean isAdmin(String username) {
        if (StringUtil.isBlank(username)) {
            return false;
        }
        try {
            UserInfo user = userConfigService.getUserInfo(username);
            return user != null && UserRoleEnum.isAdmin(user.getRoleCode());
        } catch (Exception e) {
            logger.error("检查管理员权限失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean hasGroupAccess(String username) {
        if (isAdmin(username)) {
            return true;
        }
        if (StringUtil.isBlank(username)) {
            return false;
        }
        try {
            UserInfo user = userConfigService.getUserInfo(username);
            // 只通过用户组间接关联判断是否有分组访问权限
            return user != null && StringUtil.isNotBlank(user.getUserGroupIds());
        } catch (Exception e) {
            logger.error("检查分组权限失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean hasPermission(String username, String resourceId, String permission) {
        return hasPermission(username, resourceId, null, permission);
    }

    @Override
    public boolean hasPermission(String username, String resourceId, String resourceType, String permission) {
        if (isAdmin(username)) {
            return true;
        }

        if (!hasGroupAccess(username)) {
            return false;
        }

        if (StringUtil.isBlank(resourceId)) {
            return false;
        }

        try {
            Set<String> userProjectGroupIds = getUserAccessibleProjectGroupIds(username);

            if ("mapping".equals(resourceType)) {
                // 检查任务是否属于用户可访问的任务分组
                return isMappingInProjectGroups(resourceId, userProjectGroupIds);
            } else if ("tableGroup".equals(resourceType)) {
                TableGroup tableGroup = tableGroupService.getTableGroup(resourceId);
                if (tableGroup == null) {
                    return false;
                }
                return isMappingInProjectGroups(tableGroup.getMappingId(), userProjectGroupIds);
            } else if ("projectGroup".equals(resourceType)) {
                // 直接检查任务分组权限
                return userProjectGroupIds.contains(resourceId);
            }

            return false;
        } catch (Exception e) {
            logger.error("检查权限失败: resourceId={}, resourceType={}, error={}", resourceId, resourceType, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查Mapping是否属于指定的ProjectGroups
     */
    private boolean isMappingInProjectGroups(String mappingId, Set<String> projectGroupIds) {
        if (StringUtil.isBlank(mappingId) || projectGroupIds == null || projectGroupIds.isEmpty()) {
            return false;
        }
        
        // 获取所有项目分组，检查mappingId是否在其中
        List<ProjectGroup> allProjectGroups = projectGroupService.getProjectGroupAll();
        for (ProjectGroup projectGroup : allProjectGroups) {
            if (projectGroupIds.contains(projectGroup.getId())) {
                List<String> mappingIds = projectGroup.getMappingIds();
                if (mappingIds != null && mappingIds.contains(mappingId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<String> getAccessibleMappingIds(String username) {
        if (isAdmin(username)) {
            return mappingService.getMappingAll().stream()
                    .map(MappingVo::getId)
                    .collect(Collectors.toList());
        }

        if (!hasGroupAccess(username)) {
            return Collections.emptyList();
        }

        // 获取用户可访问的任务分组ID
        Set<String> projectGroupIds = getUserAccessibleProjectGroupIds(username);
        
        // 获取所有项目分组中的mappingIds
        Set<String> accessibleMappingIds = new HashSet<>();
        List<ProjectGroup> allProjectGroups = projectGroupService.getProjectGroupAll();
        for (ProjectGroup projectGroup : allProjectGroups) {
            if (projectGroupIds.contains(projectGroup.getId())) {
                List<String> mappingIds = projectGroup.getMappingIds();
                if (mappingIds != null) {
                    accessibleMappingIds.addAll(mappingIds);
                }
            }
        }
        
        return new ArrayList<>(accessibleMappingIds);
    }

    @Override
    public boolean canAccessMapping(String username, String mappingId) {
        return hasPermission(username, mappingId, "mapping", "read");
    }

    @Override
    public boolean canAccessTableGroup(String username, String tableGroupId) {
        return hasPermission(username, tableGroupId, "tableGroup", "read");
    }

    /**
     * 获取用户可访问的任务分组ID集合
     * 只通过用户所在用户组间接关联获取
     */
    private Set<String> getUserAccessibleProjectGroupIds(String username) {
        Set<String> projectGroupIds = new HashSet<>();

        try {
            UserInfo userInfo = userConfigService.getUserInfo(username);
            if (userInfo == null) {
                return projectGroupIds;
            }

            // 只通过用户所在用户组关联的分组获取
            if (StringUtil.isNotBlank(userInfo.getUserGroupIds())) {
                String[] userGroupIds = StringUtil.split(userInfo.getUserGroupIds(), StringUtil.COMMA);
                for (String userGroupId : userGroupIds) {
                    if (StringUtil.isBlank(userGroupId)) {
                        continue;
                    }
                    UserGroup userGroup = userGroupService.getUserGroup(userGroupId.trim());
                    if (userGroup != null && userGroup.getProjectGroupIds() != null) {
                        projectGroupIds.addAll(userGroup.getProjectGroupIds());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取用户可访问任务分组失败: username={}, error={}", username, e.getMessage(), e);
        }

        return projectGroupIds;
    }
}
