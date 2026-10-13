package com.example.rbac.dto;

import com.example.rbac.entity.SysMenu;
import com.example.rbac.entity.SysUser;
import lombok.Data;

import java.util.List;

/**
 * 登录后返回的用户信息，供前端渲染菜单与按钮权限
 */
@Data
public class LoginInfoResponse {

    private SysUser user;

    /** 角色标识，如 admin */
    private List<String> roles;

    /** 权限标识，如 system:user:list */
    private List<String> permissions;

    /** 菜单树（目录 + 菜单），用于动态路由与侧边栏 */
    private List<SysMenu> menus;
}
