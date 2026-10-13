package com.example.rbac.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 权限快照：登录时从数据库加载的「权限 + 角色」集合。
 * <p>
 * 登录后固化为会话的一部分，后续请求直接复用，不再联表查询；
 * 角色授权变更时按用户精准失效，下一次请求重建。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 权限标识，如 system:user:list */
    private Set<String> permissions;

    /** 角色标识，如 admin */
    private Set<String> roles;
}
