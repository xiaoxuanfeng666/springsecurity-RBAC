-- =============================================================================
-- 营销系统 —— 独立数据库
-- 日期：2026-10-16
-- 目标库：marketing
-- =============================================================================
-- 这个库与 RBAC 库（rbac）**完全隔离**：没有共用表、没有外键、没有跨库查询。
-- 营销系统自己是一个独立的后端（marketing-backend，端口 7072），只连这个库。
--
-- 库里的表分两类：
--   ① 同步表（marketing_*）：由 RBAC 经 RocketMQ 下发后重建，本系统只读不写。
--      表名统一带 marketing_ 前缀，一眼能看出「这不是本系统的业务数据」。
--      数据是**全量快照**：每次收到消息就清空重灌，不做增量合并。
--   ② 业务表（lottery_activity / lottery_prize / lottery_activity_prize /
--      points_task / points_flow / lottery_record）：本系统自己的业务数据，与 RBAC 无关。
--
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/marketing-schema.sql
-- =============================================================================

DROP DATABASE IF EXISTS `marketing`;
CREATE DATABASE `marketing` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE `marketing`;

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
DROP TABLE IF EXISTS `marketing_user`;
CREATE TABLE `marketing_user` (
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
-- 同时承载导航（目录1/菜单2）与权限点（按钮3），perms 是接口鉴权用的权限标识。
DROP TABLE IF EXISTS `marketing_menu`;
CREATE TABLE `marketing_menu` (
    `id`         BIGINT       NOT NULL COMMENT '菜单ID（沿用 RBAC 的 sys_subsystem_menu.id）',
    `parent_id`  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    `menu_name`  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    `type`       TINYINT      NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单 3按钮',
    `perms`      VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 marketing:activity:add',
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
DROP TABLE IF EXISTS `marketing_role_menu`;
CREATE TABLE `marketing_role_menu` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,
    `role_id`    BIGINT   NOT NULL COMMENT '角色ID（RBAC 的 sys_role.id）',
    `menu_id`    BIGINT   NOT NULL COMMENT '菜单ID',
    `sync_time`  DATETIME DEFAULT NULL COMMENT '最近一次同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='同步表：角色菜单关联（RBAC 下发）';

-- ---------- 用户-角色 ----------
DROP TABLE IF EXISTS `marketing_user_role`;
CREATE TABLE `marketing_user_role` (
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

-- ---------- 抽奖活动配置 ----------
DROP TABLE IF EXISTS `lottery_activity`;
CREATE TABLE `lottery_activity` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '活动ID',
    `activity_name` VARCHAR(64)  NOT NULL COMMENT '活动名称',
    `activity_code` VARCHAR(64)  NOT NULL COMMENT '活动编码，唯一',
    `start_time`    DATETIME     DEFAULT NULL COMMENT '开始时间',
    `end_time`      DATETIME     DEFAULT NULL COMMENT '结束时间',
    `daily_limit`   INT          NOT NULL DEFAULT 1 COMMENT '每人每日抽奖次数上限',
    `prize_desc`    VARCHAR(255) DEFAULT NULL COMMENT '奖品说明',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_code` (`activity_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='抽奖活动配置';

-- ---------- 奖品配置 ----------
-- 奖品的「档案」：叫什么、什么等级、什么类型、值多少、总库存多少。
-- 与活动的投放量和中奖概率无关 —— 同一个奖品（如「10元券」）可以被多个活动各投放一批，
-- 每个活动给它设的概率与数量都不一样，那部分在 lottery_activity_prize 里。
DROP TABLE IF EXISTS `lottery_prize`;
CREATE TABLE `lottery_prize` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '奖品ID',
    `prize_name`  VARCHAR(64)   NOT NULL COMMENT '奖品名称',
    `prize_level` VARCHAR(32)   NOT NULL COMMENT '奖品等级：一等奖/二等奖/三等奖/谢谢参与',
    `prize_type`  VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '奖品类型：GOODS实物 COUPON优惠券 POINTS积分 NONE谢谢参与',
    `prize_value` INT           DEFAULT NULL COMMENT '奖品面值：优惠券金额或积分值，实物为空',
    `total_stock` INT           NOT NULL DEFAULT 0 COMMENT '总库存',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`    TINYINT       NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_prize_level` (`prize_level`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='奖品配置';

-- ---------- 活动-奖品关联 ----------
-- 「活动关联奖品表」：决定某个活动投哪些奖品，以及每个奖品在该活动里的
-- 中奖概率(%)与投放数量。概率之和允许小于 100，差额即为不中奖。
DROP TABLE IF EXISTS `lottery_activity_prize`;
CREATE TABLE `lottery_activity_prize` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `activity_id`   BIGINT        NOT NULL COMMENT '活动ID',
    `prize_id`      BIGINT        NOT NULL COMMENT '奖品ID',
    `probability`   DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '中奖概率(%)',
    `stock`         INT           NOT NULL DEFAULT 0 COMMENT '该活动投放数量',
    `sort`          INT           NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `create_time`   DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_prize` (`activity_id`, `prize_id`),
    KEY `idx_prize_id` (`prize_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='活动奖品关联';

-- ---------- 积分任务配置 ----------
DROP TABLE IF EXISTS `points_task`;
CREATE TABLE `points_task` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `task_name`   VARCHAR(64)  NOT NULL COMMENT '任务名称',
    `task_code`   VARCHAR(64)  NOT NULL COMMENT '任务编码，唯一',
    `task_type`   VARCHAR(32)  NOT NULL DEFAULT 'daily' COMMENT '任务类型：daily每日 once一次性',
    `points`      INT          NOT NULL DEFAULT 0 COMMENT '完成可得积分',
    `daily_limit` INT          NOT NULL DEFAULT 1 COMMENT '每人每日完成次数上限',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `del_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_code` (`task_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='积分任务配置';

-- ---------- 积分流水 ----------
-- 演示数据用；本系统只提供查询（真实积分变动应由业务动作触发，本期不实现）。
DROP TABLE IF EXISTS `points_flow`;
CREATE TABLE `points_flow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `username`    VARCHAR(64)  NOT NULL COMMENT '用户名（冗余，便于展示）',
    `change_type` VARCHAR(32)  NOT NULL COMMENT '类型：TASK完成积分任务 LOTTERY抽奖消耗 ADMIN人工调整',
    `points`      INT          NOT NULL COMMENT '积分变动值，正数为增加、负数为扣减',
    `balance`     INT          NOT NULL DEFAULT 0 COMMENT '变动后余额',
    `source`      VARCHAR(64)  DEFAULT NULL COMMENT '来源说明',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME     DEFAULT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='积分流水记录';

-- ---------- 抽奖记录 ----------
-- 演示数据用；本系统只提供查询。
DROP TABLE IF EXISTS `lottery_record`;
CREATE TABLE `lottery_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `activity_id`   BIGINT       NOT NULL COMMENT '活动ID',
    `activity_name` VARCHAR(64)  NOT NULL COMMENT '活动名称（冗余，便于展示）',
    `prize_id`      BIGINT       DEFAULT NULL COMMENT '奖品ID，未中奖为空',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
    `prize_name`    VARCHAR(64)  DEFAULT NULL COMMENT '奖品名称，未中奖为空',
    `prize_level`   VARCHAR(32)  DEFAULT NULL COMMENT '奖品等级：一等奖/二等奖/谢谢参与',
    `win`           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否中奖：1中奖 0未中奖',
    `draw_time`     DATETIME     DEFAULT NULL COMMENT '抽奖时间',
    PRIMARY KEY (`id`),
    KEY `idx_activity_id` (`activity_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_draw_time` (`draw_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='抽奖记录';

-- =============================================================================
-- ③ 业务演示数据
-- =============================================================================
-- 注意：这里没有营销用户的种子数据 —— 用户由 RBAC 经 MQ 下发，
-- 起服务、RBAC 推一次消息后 marketing_user 才有数据。
-- 下列流水的 user_id/username 对应 RBAC 库里的 admin(1) 与 zhangsan(2)。

INSERT INTO `lottery_activity` (`id`, `activity_name`, `activity_code`, `start_time`, `end_time`, `daily_limit`, `prize_desc`, `status`, `remark`, `del_flag`, `create_time`, `update_time`)
VALUES (1, '双十一大转盘', 'DOUBLE11_WHEEL', '2026-11-01 00:00:00', '2026-11-15 23:59:59', 3,
        '一等奖 iPhone、二等奖 100元券、三等奖 10元券', 1, '年中大促主活动', 0, NOW(), NOW()),
       (2, '新年抽红包', 'NEWYEAR_REDPACK', '2027-01-01 00:00:00', '2027-01-07 23:59:59', 1,
        '现金红包 0.5~88 元随机', 1, '元旦活动，待上线', 0, NOW(), NOW()),
       (3, '签到抽好礼', 'DAILY_SIGN', '2026-10-01 00:00:00', '2026-12-31 23:59:59', 1,
        '每日签到一次，奖品为积分', 0, '已停用的旧活动', 0, NOW(), NOW());

-- 奖品档案：可被多个活动复用
INSERT INTO `lottery_prize` (`id`, `prize_name`, `prize_level`, `prize_type`, `prize_value`, `total_stock`, `status`, `remark`, `del_flag`, `create_time`, `update_time`)
VALUES (1, 'iPhone 17', '一等奖', 'GOODS', NULL, 2, 1, '大奖，注意成本', 0, NOW(), NOW()),
       (2, '100元优惠券', '二等奖', 'COUPON', 100, 200, 1, '全场通用', 0, NOW(), NOW()),
       (3, '10元优惠券', '三等奖', 'COUPON', 10, 2000, 1, '全场通用', 0, NOW(), NOW()),
       (4, '50积分', '四等奖', 'POINTS', 50, 5000, 1, '直接充入账户积分', 0, NOW(), NOW()),
       (5, '谢谢参与', '谢谢参与', 'NONE', NULL, 0, 1, '未中奖，不占库存', 0, NOW(), NOW()),
       (6, '88元红包', '二等奖', 'COUPON', 88, 100, 0, '新年活动专用，已停用', 0, NOW(), NOW());

-- 活动-奖品关联：每个活动投哪些奖品、概率多少、投放多少
-- 概率之和 100 - 60 = 40% 即为不中奖的概率
INSERT INTO `lottery_activity_prize` (`id`, `activity_id`, `prize_id`, `probability`, `stock`, `sort`, `create_time`, `update_time`)
VALUES (1, 1, 1, 0.10, 2, 1, NOW(), NOW()),
       (2, 1, 2, 1.00, 200, 2, NOW(), NOW()),
       (3, 1, 3, 8.90, 2000, 3, NOW(), NOW()),
       (4, 1, 4, 30.00, 5000, 4, NOW(), NOW()),
       (5, 1, 5, 60.00, 0, 5, NOW(), NOW()),
       (6, 2, 6, 5.00, 100, 1, NOW(), NOW()),
       (7, 2, 3, 5.00, 500, 2, NOW(), NOW()),
       (8, 2, 5, 90.00, 0, 3, NOW(), NOW()),
       (9, 3, 4, 100.00, 1000, 1, NOW(), NOW());

INSERT INTO `points_task` (`id`, `task_name`, `task_code`, `task_type`, `points`, `daily_limit`, `status`, `remark`, `del_flag`, `create_time`, `update_time`)
VALUES (1, '每日签到', 'SIGN_IN', 'daily', 5, 1, 1, '每天签到得 5 积分', 0, NOW(), NOW()),
       (2, '浏览商品', 'BROWSE_GOODS', 'daily', 2, 10, 1, '每浏览一个商品得 2 积分', 0, NOW(), NOW()),
       (3, '首次下单', 'FIRST_ORDER', 'once', 100, 1, 1, '一次性任务', 0, NOW(), NOW()),
       (4, '邀请好友', 'INVITE_FRIEND', 'daily', 20, 3, 0, '活动已暂停', 0, NOW(), NOW());

INSERT INTO `points_flow` (`id`, `user_id`, `username`, `change_type`, `points`, `balance`, `source`, `remark`, `create_time`)
VALUES (1, 1, 'admin', 'TASK', 5, 5, '每日签到', '签到成功', '2026-10-14 09:12:00'),
       (2, 1, 'admin', 'TASK', 2, 7, '浏览商品', '浏览「秋季新品」', '2026-10-14 09:30:00'),
       (3, 1, 'admin', 'LOTTERY', -3, 4, '双十一大转盘', '抽奖 3 次', '2026-10-15 20:05:00'),
       (4, 2, 'zhangsan', 'TASK', 5, 5, '每日签到', '签到成功', '2026-10-15 08:40:00'),
       (5, 2, 'zhangsan', 'TASK', 100, 105, '首次下单', '首单奖励', '2026-10-15 21:15:00'),
       (6, 2, 'zhangsan', 'ADMIN', -50, 55, '人工调整', '兑换实物礼品', '2026-10-16 10:00:00');

INSERT INTO `lottery_record` (`id`, `activity_id`, `activity_name`, `prize_id`, `user_id`, `username`, `prize_name`, `prize_level`, `win`, `draw_time`)
VALUES (1, 1, '双十一大转盘', 3, 1, 'admin', '10元优惠券', '三等奖', 1, '2026-10-15 20:05:10'),
       (2, 1, '双十一大转盘', 5, 1, 'admin', '谢谢参与', '谢谢参与', 0, '2026-10-15 20:05:40'),
       (3, 1, '双十一大转盘', 5, 1, 'admin', '谢谢参与', '谢谢参与', 0, '2026-10-15 20:06:02'),
       (4, 1, '双十一大转盘', 2, 2, 'zhangsan', '100元优惠券', '二等奖', 1, '2026-10-15 21:20:00'),
       (5, 3, '签到抽好礼', 4, 2, 'zhangsan', '50积分', '四等奖', 1, '2026-10-16 09:05:00');
