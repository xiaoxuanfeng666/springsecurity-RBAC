-- ============================================================
-- RBAC 权限管理系统 建表脚本
-- 数据库：MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS `rbac` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `rbac`;

-- ---------- 用户表 ----------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt）',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------- 角色表 ----------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `role_key`    VARCHAR(64)  NOT NULL COMMENT '角色标识，如 admin',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT NULL,
    `update_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

-- ---------- 菜单/权限表 ----------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`   VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `path`        VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
    `component`   VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    `perms`       VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 system:user:list',
    `type`        TINYINT      NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
    `icon`        VARCHAR(64)  DEFAULT NULL COMMENT '图标',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `visible`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1显示 0隐藏',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     DEFAULT NULL,
    `update_time` DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='菜单权限表';

-- ---------- 用户-角色关联 ----------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联表';

-- ---------- 角色-菜单关联 ----------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色菜单关联表';
