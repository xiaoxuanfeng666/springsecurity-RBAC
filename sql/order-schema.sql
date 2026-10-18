-- =============================================================================
-- 订单中心 —— 独立数据库
-- 日期：2026-10-16
-- 目标库：order_center
-- =============================================================================
-- 这个库与 RBAC 库（rbac）、营销库（marketing）**完全隔离**：没有共用表、没有外键、没有跨库查询。
-- 订单中心自己是一个独立的后端（order-backend，端口 7073），只连这个库。
--
-- 库里的表分两类：
--   ① 同步表（order_*）：由 RBAC 经 RocketMQ 下发后重建，本系统只读不写。
--      表名统一带 order_ 前缀，一眼能看出「这不是本系统的业务数据」。
--      数据是**全量快照**：每次收到消息就清空重灌，不做增量合并。
--   ② 业务表（order_info）：本系统自己的业务数据，与 RBAC 无关。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/order-schema.sql
-- =============================================================================

DROP DATABASE IF EXISTS `order_center`;
CREATE DATABASE `order_center` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE `order_center`;

-- =============================================================================
-- ① 同步表：RBAC 下发的用户 / 角色 / 菜单 / 权限
-- =============================================================================
-- 本系统没有自己的权限管理，这些表的内容全部由 MQ 消息决定，
-- 收到消息后整体清空重建。手动改这些表没有意义，下一次同步就会被覆盖。

-- ---------- 用户 ----------
-- id 直接沿用 RBAC 库的 sys_user.id（不是自增），这样子系统之间能对上同一个人；
-- role_keys 是「该用户的角色标识」，逗号分隔（如 admin / common）。
-- role_keys 里含 admin 即为超级管理员：RBAC 侧超管的授权不落库，
-- 只能靠角色标识识别，本系统据此给通配符 *:*:* 与全部菜单。
DROP TABLE IF EXISTS `order_user`;
CREATE TABLE `order_user` (
    `id`         BIGINT       NOT NULL COMMENT '用户ID（沿用 RBAC 的 sys_user.id）',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录名，OAuth2 令牌里的唯一身份',
    `nickname`   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1在职 0停用 2离职',
    `role_keys`  VARCHAR(255) DEFAULT NULL COMMENT '角色标识，逗号分隔',
    `subsystem_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '是否本子系统管理员：1是 0否',
    `sync_time`  DATETIME     DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：用户（RBAC 下发）';

-- ---------- 菜单 / 权限点 ----------
-- 同时承载导航（目录1/菜单2）与权限点（按钮3），perms 是接口鉴权用的权限标识。
DROP TABLE IF EXISTS `order_menu`;
CREATE TABLE `order_menu` (
    `id`         BIGINT       NOT NULL COMMENT '菜单ID（沿用 RBAC 的 sys_subsystem_menu.id）',
    `parent_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `type`       TINYINT      NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单 3按钮',
    `perms`      VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 order:order:add',
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
DROP TABLE IF EXISTS `order_role_menu`;
CREATE TABLE `order_role_menu` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID（RBAC 的 sys_role.id）',
    `menu_id`    BIGINT   NOT NULL COMMENT '菜单ID',
    `sync_time`  DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：角色菜单关联（RBAC 下发）';

-- ---------- 用户-角色 ----------
DROP TABLE IF EXISTS `order_user_role`;
CREATE TABLE `order_user_role` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
    `sync_time`  DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：用户角色关联（RBAC 下发）';

-- =============================================================================
-- ② 业务表：本系统自己的数据
-- =============================================================================

-- ---------- 订单 ----------
-- 表名是 order_info 而不是 order：后者是 MySQL 保留字，用了就得处处加反引号。
-- customer_name 存文本即可 —— 客户档案不归订单中心管，本系统只记下单人是谁。
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`      VARCHAR(64)    NOT NULL COMMENT '订单编号，唯一',
    `customer_name` VARCHAR(64)    NOT NULL COMMENT '客户名称',
    `order_amount`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1待支付 2已支付 3已发货 4已完成 5已取消',
    `order_time`    DATETIME       DEFAULT NULL COMMENT '下单时间',
    `remark`        VARCHAR(255)   DEFAULT NULL COMMENT '备注',
    `del_flag`      TINYINT        NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time`   DATETIME       DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_customer_name` (`customer_name`),
    KEY `idx_status` (`status`),
    KEY `idx_order_time` (`order_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单';

-- =============================================================================
-- ③ 业务演示数据
-- =============================================================================
-- 注意：这里没有订单中心用户的种子数据 —— 用户由 RBAC 经 MQ 下发，
-- 起服务、RBAC 推一次消息后 order_user 才有数据。
-- 下列订单仅是业务演示，不依赖任何同步数据。

INSERT INTO `order_info` (`id`, `order_no`, `customer_name`, `order_amount`, `status`, `order_time`, `remark`, `del_flag`, `create_time`, `update_time`)
VALUES (1, 'ORD20261010001', '张三', 1299.00, 4, '2026-10-10 10:12:00', '已签收', 0, NOW(), NOW()),
       (2, 'ORD20261011002', '李四', 199.50, 2, '2026-10-11 14:30:00', '等待发货', 0, NOW(), NOW()),
       (3, 'ORD20261012003', '王五', 88.00, 1, '2026-10-12 09:05:00', '用户尚未付款', 0, NOW(), NOW()),
       (4, 'ORD20261013004', '张三', 2560.00, 3, '2026-10-13 16:48:00', '已出库', 0, NOW(), NOW()),
       (5, 'ORD20261014005', '赵六', 15.90, 5, '2026-10-14 20:21:00', '用户主动取消', 0, NOW(), NOW()),
       (6, 'ORD20261015006', '李四', 780.00, 4, '2026-10-15 11:02:00', '已签收', 0, NOW(), NOW());
