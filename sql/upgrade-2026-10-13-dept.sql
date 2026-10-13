-- ============================================================
-- 增量升级脚本：新增「部门」模块
-- 适用：已按旧版 schema.sql / data.sql 初始化过的数据库
-- 说明：本脚本一次性执行，重复执行会因 ALTER 报错
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < upgrade-2026-10-13-dept.sql
-- ============================================================

USE `rbac`;

-- ---------- 1. 部门表 ----------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0为顶级',
    `dept_name`   VARCHAR(64)  NOT NULL COMMENT '部门名称',
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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='部门表';

-- ---------- 2. 用户表增加部门归属 ----------
ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID' AFTER `nickname`,
    ADD KEY `idx_dept_id` (`dept_id`);

-- ---------- 3. 部门初始数据 ----------
INSERT INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `sort`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_time`, `update_time`)
VALUES (1, 0, '总公司', 1, '张三丰', '13800000000', 'hq@example.com', 1, 0, NOW(), NOW()),
       (2, 1, '研发部', 1, '李四', '13800000010', 'rd@example.com', 1, 0, NOW(), NOW()),
       (3, 1, '市场部', 2, '王五', '13800000020', 'mkt@example.com', 1, 0, NOW(), NOW()),
       (4, 2, '前端组', 1, '赵六', '13800000030', 'fe@example.com', 1, 0, NOW(), NOW()),
       (5, 2, '后端组', 2, '钱七', '13800000040', 'be@example.com', 1, 0, NOW(), NOW());

-- ---------- 4. 已有用户归属部门 ----------
UPDATE `sys_user` SET `dept_id` = 1 WHERE `username` = 'admin';
UPDATE `sys_user` SET `dept_id` = 4 WHERE `username` = 'zhangsan';

-- ---------- 5. 部门菜单与按钮权限 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (103, 1, '部门管理', 'dept', 'system/dept/index', 'system:dept:list', 2, 'OfficeBuilding', 4, 1, 1, NOW(), NOW()),
       (1031, 103, '部门查询', NULL, NULL, 'system:dept:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1032, 103, '部门新增', NULL, NULL, 'system:dept:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1033, 103, '部门修改', NULL, NULL, 'system:dept:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1034, 103, '部门删除', NULL, NULL, 'system:dept:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- 6. 授权给 admin 角色 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (103, 1031, 1032, 1033, 1034);
