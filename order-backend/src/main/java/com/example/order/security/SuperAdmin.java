package com.example.order.security;

/**
 * 超级管理员的隐式权限
 * <p>
 * 与 RBAC 侧同一套语义：{@code admin} 角色的权限**不落库**，
 * 因此 MQ 消息里的 roleMenus 没有它的记录。本系统靠用户行上的 roleKeys
 * 判断「这是超级管理员」，直接给通配符与全部菜单 —— 否则 RBAC 新增一个菜单，
 * 就得靠人工去补授权，超级管理员反而会"没权限"。
 * <p>
 * 代价同 RBAC：超管权限无法收窄，永远是全集。
 */
public final class SuperAdmin {

    /** 超级管理员的角色标识，与 RBAC 的 sys_role.role_key 一致 */
    public static final String ROLE_KEY = "admin";

    /** 通配符权限，见到它一律放行 */
    public static final String WILDCARD_PERMISSION = "*:*:*";

    private SuperAdmin() {
    }

    public static boolean matches(java.util.Collection<String> roleKeys) {
        return roleKeys != null && roleKeys.contains(ROLE_KEY);
    }
}
