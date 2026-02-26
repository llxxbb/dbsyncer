package org.dbsyncer.web.config;

import org.dbsyncer.biz.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final PermissionService permissionService;

    public CustomPermissionEvaluator(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetId, Object permission) {
        if (authentication == null || targetId == null || permission == null) {
            return false;
        }

        String username = authentication.getName();
        String resourceId = String.valueOf(targetId);
        String permissionType = String.valueOf(permission);

        logger.debug("检查权限: username={}, resourceId={}, permission={}", username, resourceId, permissionType);

        return permissionService.hasPermission(username, resourceId, permissionType);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }

        String username = authentication.getName();
        String resourceId = String.valueOf(targetId);
        String permissionType = String.valueOf(permission);

        logger.debug("检查权限: username={}, resourceId={}, targetType={}, permission={}", 
                username, resourceId, targetType, permissionType);

        return permissionService.hasPermission(username, resourceId, targetType, permissionType);
    }
}
