package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.PermissionService;
import org.dbsyncer.biz.ProjectGroupService;
import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.enums.UserRoleEnum;
import org.dbsyncer.biz.vo.MappingVo;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.model.ProjectGroup;
import org.dbsyncer.parser.model.TableGroup;
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
            return user != null && StringUtil.isNotBlank(user.getGroupIds());
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
            Set<String> userMappingIds = getUserAccessibleMappingIds(username);

            if ("mapping".equals(resourceType)) {
                return userMappingIds.contains(resourceId);
            } else if ("tableGroup".equals(resourceType)) {
                TableGroup tableGroup = tableGroupService.getTableGroup(resourceId);
                if (tableGroup == null) {
                    return false;
                }
                return userMappingIds.contains(tableGroup.getMappingId());
            }

            return userMappingIds.contains(resourceId);
        } catch (Exception e) {
            logger.error("检查权限失败: resourceId={}, resourceType={}, error={}", resourceId, resourceType, e.getMessage(), e);
            return false;
        }
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

        return new ArrayList<>(getUserAccessibleMappingIds(username));
    }

    @Override
    public boolean canAccessMapping(String username, String mappingId) {
        return hasPermission(username, mappingId, "mapping", "read");
    }

    @Override
    public boolean canAccessTableGroup(String username, String tableGroupId) {
        return hasPermission(username, tableGroupId, "tableGroup", "read");
    }

    private Set<String> getUserAccessibleMappingIds(String username) {
        Set<String> mappingIds = new HashSet<>();

        List<ProjectGroup> userGroups = projectGroupService.getProjectGroupsByUser(username);
        if (userGroups == null || userGroups.isEmpty()) {
            return mappingIds;
        }

        for (ProjectGroup group : userGroups) {
            List<String> groupMappingIds = group.getMappingIds();
            if (groupMappingIds != null) {
                mappingIds.addAll(groupMappingIds);
            }
        }

        return mappingIds;
    }
}
