package com.example.rbac.security;

import com.example.rbac.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 登录快照：重建认证主体（{@link LoginUser}）所需的全部数据 —— 用户信息 + 权限 + 角色。
 * <p>
 * 登录时查库一次固化为会话的一部分，之后每个请求直接复用，**不再访问数据库**；
 * 角色授权变更、用户资料变更或用户角色变更时按用户精准失效，下一次请求重建。
 * <p>
 * 注意：密码散列不在快照里（{@code SysUser.password} 标注了 {@code @JsonIgnore}），
 * 需要校验密码的场景要单独查库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户信息（不含密码） */
    private SysUser user;

    /** 权限标识，如 system:user:list */
    private Set<String> permissions;

    /** 角色标识，如 admin */
    private Set<String> roles;
}
