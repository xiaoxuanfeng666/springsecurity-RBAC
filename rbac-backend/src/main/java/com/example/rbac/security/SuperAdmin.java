package com.example.rbac.security;

import java.util.Collection;

/**
 * 超级管理员的隐式权限
 * <p>
 * 超级管理员的权限**不落库**：不写 sys_role_menu，也不需要给每个新菜单手动补授权。
 * 权限判定与菜单渲染在代码里特判这个角色：
 * <ul>
 *   <li>权限集合直接替换为通配符 {@link #WILDCARD_PERMISSION}，{@link PermissionService} 见到通配符一律放行</li>
 *   <li>菜单树返回全部启用菜单，而不是按 sys_role_menu 过滤</li>
 * </ul>
 * 这样新增菜单后超级管理员天然可见可用，不需要动数据库。
 * <p>
 * 代价：超级管理员的权限无法被"收窄"——它永远是全集，这是有意的设计。
 */
public final class SuperAdmin {

    /** 超级管理员的角色标识（sys_role.role_key） */
    public static final String ROLE_KEY = "admin";

    /** 通配权限：持有它即通过一切 hasPerm 判定 */
    public static final String WILDCARD_PERMISSION = "*:*:*";

    private SuperAdmin() {
    }

    /**
     * 判断这组角色标识里是否含超级管理员
     */
    public static boolean matches(Collection<String> roleKeys) {
        return roleKeys != null && roleKeys.contains(ROLE_KEY);
    }
}
