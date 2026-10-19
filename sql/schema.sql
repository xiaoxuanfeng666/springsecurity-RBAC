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
    `employee_no` VARCHAR(64)  DEFAULT NULL COMMENT '工号',
    `alias`       VARCHAR(64)  DEFAULT NULL COMMENT '别名',
    `position`    VARCHAR(128) DEFAULT NULL COMMENT '职务',
    `gender`      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
    `dept_id`     BIGINT       DEFAULT NULL COMMENT '主部门ID（公司/分公司/部门）',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `biz_mail`    VARCHAR(128) DEFAULT NULL COMMENT '企业邮箱',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `telephone`   VARCHAR(32)  DEFAULT NULL COMMENT '座机',
    `direct_leader` VARCHAR(255) DEFAULT NULL COMMENT '直属上级（外部userid/用户名，多个逗号分隔）',
    `external_position` VARCHAR(128) DEFAULT NULL COMMENT '对外职务',
    `external_nickname` VARCHAR(64) DEFAULT NULL COMMENT '对外昵称',
    `address`     VARCHAR(128) DEFAULT NULL COMMENT '地址',
    `ext_attr`    TEXT         COMMENT '扩展属性（JSON）',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1在职 0停用 2离职',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_employee_no` (`employee_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------- 用户与部门关联（多部门）----------
DROP TABLE IF EXISTS `sys_user_dept`;
CREATE TABLE `sys_user_dept` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`       BIGINT   NOT NULL COMMENT '用户ID',
    `dept_id`       BIGINT   NOT NULL COMMENT '部门ID',
    `is_main`       TINYINT  NOT NULL DEFAULT 0 COMMENT '是否主部门：1是 0否',
    `is_leader`     TINYINT  NOT NULL DEFAULT 0 COMMENT '是否该部门负责人：1是 0否',
    `order_in_dept` INT      NOT NULL DEFAULT 0 COMMENT '部门内排序',
    `create_time`   DATETIME DEFAULT NULL,
    `update_time`   DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_dept` (`user_id`, `dept_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户与部门关联（多部门）';

-- ---------- 组织架构表（公司 / 分公司 / 部门）----------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '组织节点ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点ID，0为顶级',
    `dept_name`   VARCHAR(64)  NOT NULL COMMENT '节点名称',
    `type`        TINYINT      NOT NULL DEFAULT 3 COMMENT '类型：1公司 2分公司 3部门',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `leader`      VARCHAR(64)  DEFAULT NULL COMMENT '负责人',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='组织架构表（公司/分公司/部门）';

-- ---------- 岗位表 ----------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
    `post_code`   VARCHAR(64)  NOT NULL COMMENT '岗位编码',
    `post_name`   VARCHAR(64)  NOT NULL COMMENT '岗位名称',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_code` (`post_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='岗位表';

-- ---------- 用户-岗位关联 ----------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `post_id` BIGINT NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户岗位关联表';

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

-- ---------- 操作日志 ----------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`        VARCHAR(64)  DEFAULT NULL COMMENT '模块，如 用户管理',
    `action`        VARCHAR(32)  NOT NULL COMMENT '动作：LOGIN/LOGOUT/KICK/DISABLED/ENABLED/RESIGNED/RESET_PWD/CHANGE_PWD/CREATE/UPDATE/DELETE/GRANT',
    `description`   VARCHAR(128) DEFAULT NULL COMMENT '操作说明',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '目标用户ID',
    `username`      VARCHAR(64)  DEFAULT NULL COMMENT '目标用户名',
    `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人ID（本人操作时与目标一致）',
    `operator_name` VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    `http_method`   VARCHAR(10)  DEFAULT NULL COMMENT '请求方法',
    `request_uri`   VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    `success`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    `error_msg`     VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    `duration_ms`   BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志';

-- ---------- OAuth2 客户端配置 ----------
-- 沿用 Spring Security OAuth 的 clientdetails 约定：主键为非自增的 appId，列名为驼峰，
-- grantTypes / scope / redirectUrl / authorities / autoApproveScopes 均为逗号分隔多值
-- appSecret 为 NOT NULL：客户端密钥不允许为空（空密钥等同于公开客户端，任何人都能换令牌），
-- 存储的是 AES 密文（密钥来自配置项 oauth2.client-secret-key）—— 用可逆加密而非哈希，
-- 是为了让「客户端管理」页面能展示明文供复制给第三方。
DROP TABLE IF EXISTS `clientdetails`;
CREATE TABLE `clientdetails` (
    `appId`                  VARCHAR(128)  NOT NULL COMMENT '客户端标识，唯一',
    `resourceIds`            VARCHAR(255)  DEFAULT NULL COMMENT '资源ID，逗号分隔',
    `appSecret`              VARCHAR(255)  NOT NULL COMMENT '客户端密钥（AES 密文，不允许为空）',
    `scope`                  VARCHAR(255)  DEFAULT NULL COMMENT '授权范围，逗号分隔',
    `grantTypes`             VARCHAR(255)  DEFAULT NULL COMMENT '授权模式，逗号分隔：authorization_code/client_credentials/password/refresh_token/implicit',
    `redirectUrl`            VARCHAR(255)  DEFAULT NULL COMMENT '回调地址，逗号分隔',
    `authorities`            VARCHAR(255)  DEFAULT NULL COMMENT '客户端本身拥有的权限，逗号分隔',
    `access_token_validity`  INT           DEFAULT NULL COMMENT '访问令牌有效期（秒）',
    `refresh_token_validity` INT           DEFAULT NULL COMMENT '刷新令牌有效期（秒）',
    `additionalInformation`  VARCHAR(4096) DEFAULT NULL COMMENT '扩展信息（JSON）',
    `autoApproveScopes`      VARCHAR(255)  DEFAULT NULL COMMENT '自动放行的授权范围，逗号分隔',
    PRIMARY KEY (`appId`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='OAuth2 客户端配置';

-- ============================================================
-- 子系统菜单权限
-- ============================================================
-- 这套表服务于「RBAC 作为中枢，统一给各子系统配菜单权限」：
--   sys_subsystem             登记有哪些子系统
--   sys_subsystem_menu        每个子系统自己的菜单树（目录 + 菜单 + 按钮，带 perms 权限标识）
--   sys_role_subsystem_menu   角色能看子系统里的哪些菜单
-- 与 sys_menu / sys_role_menu 完全独立：那两张表管的是 RBAC 后台自己的导航与权限点，
-- 这里的菜单不参与 RBAC 的 @PreAuthorize 判定，只用于下发给子系统，由子系统自己鉴权。

-- ---------- 子系统登记 ----------
DROP TABLE IF EXISTS `sys_subsystem`;
CREATE TABLE `sys_subsystem` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '子系统ID',
    `subsystem_code` VARCHAR(64)  NOT NULL COMMENT '子系统编码，如 order-center',
    `subsystem_name` VARCHAR(64)  NOT NULL COMMENT '子系统名称',
    `sort`           INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `remark`         VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subsystem_code` (`subsystem_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='子系统登记表';

-- ---------- 子系统菜单 ----------
-- type：1目录 / 2菜单 / 3按钮。目录与菜单是导航，按钮是权限点，
-- 三者的 perms 都会被下发给子系统，由子系统在接口上判权。
DROP TABLE IF EXISTS `sys_subsystem_menu`;
CREATE TABLE `sys_subsystem_menu` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `subsystem_id` BIGINT       NOT NULL COMMENT '所属子系统ID',
    `parent_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`    VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `type`         TINYINT      NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单 3按钮',
    `perms`        VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 marketing:activity:add',
    `path`         VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
    `component`    VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    `icon`         VARCHAR(64)  DEFAULT NULL COMMENT '图标',
    `sort`         INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `visible`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1显示 0隐藏',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_subsystem_id` (`subsystem_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='子系统菜单表';

-- ---------- 角色-子系统菜单关联 ----------
DROP TABLE IF EXISTS `sys_role_subsystem_menu`;
CREATE TABLE `sys_role_subsystem_menu` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`           BIGINT NOT NULL COMMENT '角色ID',
    `subsystem_menu_id` BIGINT NOT NULL COMMENT '子系统菜单ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_subsystem_menu` (`role_id`, `subsystem_menu_id`),
    KEY `idx_subsystem_menu_id` (`subsystem_menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色子系统菜单关联表';

