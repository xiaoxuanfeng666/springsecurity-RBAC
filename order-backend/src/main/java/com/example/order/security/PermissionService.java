package com.example.order.security;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限校验服务，配合 @PreAuthorize("@ss.hasPerm('order:order:add')") 使用
 * <p>
 * 权限来自登录用户的 permissions，即本地的同步表（由 RBAC 经 MQ 下发）。
 * 超级管理员持通配符 {@link SuperAdmin#WILDCARD_PERMISSION}，一律放行。
 */
@Service("ss")
public class PermissionService {

    public boolean hasPerm(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getPermissions() == null) {
            return false;
        }
        Set<String> perms = loginUser.getPermissions();
        return perms.contains(SuperAdmin.WILDCARD_PERMISSION) || perms.contains(permission);
    }

    public boolean hasAnyPerm(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String permission : permissions) {
            if (hasPerm(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRole(String roleKey) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getRoles() == null) {
            return false;
        }
        return loginUser.getRoles().contains(roleKey);
    }
}
