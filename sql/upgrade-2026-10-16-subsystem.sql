-- ============================================================
-- 增量升级：子系统菜单权限（RBAC 统一给子系统配菜单）
-- 适用于已按 schema.sql 初始化过的旧库；全新安装请把本文件内容合并进 schema.sql + data.sql
-- 注意：本脚本一次性执行，重复执行会因 CREATE TABLE / 主键冲突而报错
--
-- 【执行后必做】清理 Redis 中的权限快照，否则已登录用户（含 admin）读到的仍是登录时的旧快照，
-- 看不到新菜单，必须重新登录才行。快照只是缓存，清掉不会把人踢下线，下一次请求会自动重建：
--   redis-cli EVAL "local ks=redis.call('keys','login:perm:*'); for i,k in ipairs(ks) do redis.call('del',k) end; return #ks" 0
-- ============================================================

USE `rbac`;

-- ---------- 子系统登记表 ----------
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

-- ---------- 子系统菜单表 ----------
-- type 只有 1目录 / 2菜单（不含按钮），因此没有 perms 列：这里的菜单是导航，不是权限点。
CREATE TABLE `sys_subsystem_menu` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `subsystem_id` BIGINT       NOT NULL COMMENT '所属子系统ID',
    `parent_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`    VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `type`         TINYINT      NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单',
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

-- ---------- 角色-子系统菜单关联表 ----------
CREATE TABLE `sys_role_subsystem_menu` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`           BIGINT NOT NULL COMMENT '角色ID',
    `subsystem_menu_id` BIGINT NOT NULL COMMENT '子系统菜单ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_subsystem_menu` (`role_id`, `subsystem_menu_id`),
    KEY `idx_subsystem_menu_id` (`subsystem_menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色子系统菜单关联表';

-- ---------- 菜单：子系统管理 / 子系统菜单管理 ----------
-- 挂到现有「系统管理」目录（id=1）下，与业务菜单并列
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (108, 1, '子系统管理', 'subsystem', 'system/subsystem/index', 'system:subsystem:list', 2, 'Grid', 8, 1, 1, NOW(), NOW()),
       (109, 1, '子系统菜单管理', 'subsystem-menu', 'system/subsystem-menu/index', 'system:subsystem:menu:list', 2, 'Menu', 9, 1, 1, NOW(), NOW());

-- ---------- 按钮权限：子系统管理 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1081, 108, '子系统查询', NULL, NULL, 'system:subsystem:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1082, 108, '子系统新增', NULL, NULL, 'system:subsystem:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1083, 108, '子系统修改', NULL, NULL, 'system:subsystem:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1084, 108, '子系统删除', NULL, NULL, 'system:subsystem:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- 按钮权限：子系统菜单管理 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1091, 109, '子系统菜单查询', NULL, NULL, 'system:subsystem:menu:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1092, 109, '子系统菜单新增', NULL, NULL, 'system:subsystem:menu:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1093, 109, '子系统菜单修改', NULL, NULL, 'system:subsystem:menu:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1094, 109, '子系统菜单删除', NULL, NULL, 'system:subsystem:menu:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- 角色-菜单：无需给 admin 授权 ----------
-- 超级管理员的权限由代码隐式授予（通配符 + 全部菜单），sys_role_menu 里刻意没有它的行。
-- 所以上面这些新菜单不需要（也不应该）给 role_id=1 插记录。

-- ---------- 示例子系统及其菜单 ----------
-- sys_subsystem_menu 里的菜单只用于下发给子系统，与 RBAC 后台自己的 sys_menu 无关。
INSERT INTO `sys_subsystem` (`id`, `subsystem_code`, `subsystem_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'order-center', '订单中心', 1, 1, 0, '示例子系统：演示菜单下发', NOW(), NOW());

INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1, 1, 0, '订单管理', 1, '/order', 'Layout', 'Document', 1, 1, 1, NOW(), NOW()),
       (2, 1, 1, '订单列表', 2, 'list', 'order/list/index', 'List', 1, 1, 1, NOW(), NOW()),
       (3, 1, 1, '订单统计', 2, 'stat', 'order/stat/index', 'DataLine', 2, 1, 1, NOW(), NOW()),
       (4, 1, 0, '客户管理', 1, '/customer', 'Layout', 'User', 2, 1, 1, NOW(), NOW()),
       (5, 1, 4, '客户列表', 2, 'list', 'customer/list/index', 'UserFilled', 1, 1, 1, NOW(), NOW());
