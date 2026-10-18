package com.example.order.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RBAC 下发的子系统同步消息
 * <p>
 * 与 rbac-backend 的 {@code com.example.rbac.mq.SubsystemSyncMessage} 字段一一对应，
 * 但两个服务是独立部署的，不做类共享 —— 这里保留自己的副本，
 * 对端加字段时靠 {@code ignoreUnknown} 兼容，不会因为别人发版而解析失败。
 * <p>
 * 消息是**全量快照**：收到即清空重建本地同步表，不处理增量。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubsystemSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long subsystemId;

    private String subsystemCode;

    private String subsystemName;

    private List<Menu> menus = new ArrayList<>();

    private List<RoleMenu> roleMenus = new ArrayList<>();

    private List<UserRole> userRoles = new ArrayList<>();

    private List<User> users = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Menu implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private Long parentId;

        private String menuName;

        /** 1目录 2菜单 3按钮 */
        private Integer type;

        private String perms;

        private String path;

        private String component;

        private String icon;

        private Integer sort;

        private Integer visible;

        private Integer status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoleMenu implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long roleId;

        private Long menuId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserRole implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long userId;

        private Long roleId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long id;

        private String username;

        private String nickname;

        private Integer status;


        /** 角色标识；含 admin 即为超级管理员（RBAC 侧超管授权不落库，只能这么识别） */
        private List<String> roleKeys = new ArrayList<>();
    }
}
