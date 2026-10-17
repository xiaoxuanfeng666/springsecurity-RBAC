-- =============================================================================
-- 审计日志系统 —— 独立数据库
-- 日期：2026-10-17
-- 目标库：audit_log
-- =============================================================================
-- 这个库与 RBAC 库（rbac）**完全隔离**：没有共用表、没有外键、没有跨库查询。
-- 审计日志系统自己是一个独立的后端（audit-backend，端口 7074），只连这个库。
--
-- 库里的表分两类：
--   ① 同步表（audit_user / audit_menu / audit_role_menu / audit_user_role）：
--      由 RBAC 经 RocketMQ 下发后重建，本系统只读不写。
--   ② 业务表（audit_log）：各系统经 MQ（主题 RBAC_AUDIT_LOG）上报的
--      增删改操作审计记录。本系统只做接收与查询，不写入业务动作。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/audit-schema.sql
-- =============================================================================

DROP DATABASE IF EXISTS `audit_log`;
CREATE DATABASE `audit_log` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE `audit_log`;

-- =============================================================================
-- ① 同步表：RBAC 下发的用户 / 角色 / 菜单 / 权限
-- =============================================================================

-- ---------- 用户 ----------
DROP TABLE IF EXISTS `audit_user`;
CREATE TABLE `audit_user` (
    `id`         BIGINT       NOT NULL COMMENT '用户ID（沿用 RBAC 的 sys_user.id）',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录名，OAuth2 令牌里的唯一身份',
    `nickname`   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1在职 0停用 2离职',
    `role_keys`  VARCHAR(255) DEFAULT NULL COMMENT '角色标识，逗号分隔',
    `sync_time`  DATETIME     DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：用户（RBAC 下发）';

-- ---------- 菜单 / 权限点 ----------
DROP TABLE IF EXISTS `audit_menu`;
CREATE TABLE `audit_menu` (
    `id`         BIGINT       NOT NULL COMMENT '菜单ID（沿用 RBAC 的 sys_subsystem_menu.id）',
    `parent_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `type`       TINYINT      NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单 3按钮',
    `perms`      VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 audit:log:list',
    `path`       VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
    `component`  VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    `icon`       VARCHAR(64)  DEFAULT NULL COMMENT '图标',
    `sort`       INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `visible`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1显示 0隐藏',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `sync_time`  DATETIME     DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：菜单与权限点（RBAC 下发）';

-- ---------- 角色-菜单 ----------
DROP TABLE IF EXISTS `audit_role_menu`;
CREATE TABLE `audit_role_menu` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID（RBAC 的 sys_role.id）',
    `menu_id`    BIGINT   NOT NULL COMMENT '菜单ID',
    `sync_time`  DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：角色菜单关联（RBAC 下发）';

-- ---------- 用户-角色 ----------
DROP TABLE IF EXISTS `audit_user_role`;
CREATE TABLE `audit_user_role` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
    `sync_time`  DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：用户角色关联（RBAC 下发）';

-- =============================================================================
-- ② 业务表：审计日志
-- =============================================================================
-- 只记录接口的增删改（CREATE/UPDATE/DELETE/GRANT），**不记录登录/登出**。
-- 各业务系统在自己的 @OperLog 切面里组装消息并发到 RBAC_AUDIT_LOG，
-- 本系统 Kafka/RocketMQ 消费者落库。
--
-- request_params 是接口请求参数的 JSON 快照（已脱敏、已截断到 8000 字符）。
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审计ID',
    `source`         VARCHAR(32)  NOT NULL COMMENT '来源系统：rbac / marketing / order-center',
    `module`         VARCHAR(64)  DEFAULT NULL COMMENT '业务模块，如 用户管理/订单管理',
    `action`         VARCHAR(16)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/GRANT',
    `description`    VARCHAR(255) DEFAULT NULL COMMENT '操作说明',
    `operator_id`    BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `operator_name`  VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    `http_method`    VARCHAR(16)  DEFAULT NULL COMMENT 'HTTP 方法',
    `request_uri`    VARCHAR(255) DEFAULT NULL COMMENT '请求路径（不含查询串）',
    `request_params` TEXT         COMMENT '接口请求参数 JSON（已脱敏、已截断）',
    `success`        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败',
    `error_msg`      VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
    `duration_ms`    BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）',
    `ip`             VARCHAR(64)  DEFAULT NULL COMMENT '操作人 IP',
    `create_time`    DATETIME     NOT NULL COMMENT '事件时间',
    PRIMARY KEY (`id`),
    KEY `idx_source` (`source`),
    KEY `idx_action` (`action`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计日志：各系统增删改操作';
