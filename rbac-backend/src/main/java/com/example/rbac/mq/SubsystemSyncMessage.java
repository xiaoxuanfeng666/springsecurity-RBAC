package com.example.rbac.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 下发给子系统的同步消息：某个子系统的「菜单 + 角色菜单 + 用户角色 + 用户」全量快照。
 * <p>
 * 消息体是**全量**而非增量：子系统收到后清空重建本地同步表即可，不用处理增量顺序问题。
 * 序列化为 JSON 字符串后投递（topic {@code RBAC_SUBSYSTEM_SYNC}，tag 为子系统编码），
 * 子系统有自己的 DTO 副本，不做类共享。
 * <p>
 * {@code users[].roleKeys} 是必需的：超级管理员的授权不落库（见
 * {@link com.example.rbac.security.SuperAdmin}），{@code roleMenus} 里没有它的记录，
 * 子系统只能靠 roleKeys 含 {@code admin} 来判断"这是超级管理员，给全集权限"。
 */
@Data
public class SubsystemSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long subsystemId;

    private String subsystemCode;

    private String subsystemName;

    private List<Menu> menus = new ArrayList<>();

    private List<RoleMenu> roleMenus = new ArrayList<>();

    private List<UserRole> userRoles = new ArrayList<>();

    private List<User> users = new ArrayList<>();

    /** 下发给子系统的菜单 / 权限点 */
    @Data
    public static class Menu implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private Long parentId;

        private String menuName;

        /** 1目录 2菜单 3按钮 */
        private Integer type;

        private String path;

        private String component;

        private String icon;

        private Integer sort;

        private Integer visible;

        private Integer status;

        /** 权限标识，子系统用它做接口鉴权 */
        private String perms;
    }

    /** 角色-子系统菜单关联 */
    @Data
    public static class RoleMenu implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long roleId;

        private Long menuId;
    }

    /** 用户-角色关联 */
    @Data
    public static class UserRole implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long roleId;
    }

    /** 用户（只带子系统用得到的字段，不含密码等敏感信息） */
    @Data
    public static class User implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String username;

        private String nickname;

        /** 1在职 0停用 2离职 */
        private Integer status;

        private List<String> roleKeys = new ArrayList<>();
    }
}
