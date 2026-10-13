package com.example.rbac.security;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限校验服务，配合 @PreAuthorize("@ss.hasPerm('system:user:list')") 使用
 * <p>
 * 权限来源为每次请求实时查询的 LoginUser.permissions，
 * 因此角色权限被动态修改后无需重新登录即可生效。
 */
@Service("ss")
public class PermissionService {

    /** 超级权限通配符 */
    private static final String ALL_PERMISSION = "*:*:*";

    public boolean hasPerm(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getPermissions() == null) {
            return false;
        }
        Set<String> perms = loginUser.getPermissions();
        return perms.contains(ALL_PERMISSION) || perms.contains(permission);
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
