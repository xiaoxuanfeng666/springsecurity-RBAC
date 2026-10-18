package com.example.audit.dto;

import com.example.audit.entity.AuditMenu;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户的信息：身份 + 角色 + 权限点 + 菜单树
 * <p>
 * 前端拿它做按钮级权限控制与动态路由。数据全部来自 RBAC 经 MQ 下发的同步表。
 */
@Data
public class LoginInfoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String nickname;


    private Set<String> roles;

    private Set<String> permissions;

    private List<AuditMenu> menus;
}
