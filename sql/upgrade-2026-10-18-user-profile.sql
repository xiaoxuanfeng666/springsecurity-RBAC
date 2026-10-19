-- =============================================================================
-- 增量升级：用户宽表字段 + 多部门
-- 日期：2026-10-18
-- 目标库：rbac
-- =============================================================================
-- 参考企业微信「创建成员」接口字段，补齐本地用户资料；
-- 新增 sys_user_dept 支持一个用户属于多个部门，sys_user.dept_id 继续作为主部门。
--
-- 执行：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-user-profile.sql
-- =============================================================================

USE `rbac`;

ALTER TABLE `sys_user`
    ADD COLUMN `employee_no`       VARCHAR(64)  DEFAULT NULL COMMENT '工号' AFTER `nickname`,
    ADD COLUMN `alias`             VARCHAR(64)  DEFAULT NULL COMMENT '别名' AFTER `employee_no`,
    ADD COLUMN `position`          VARCHAR(128) DEFAULT NULL COMMENT '职务' AFTER `alias`,
    ADD COLUMN `gender`            TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女' AFTER `position`,
    ADD COLUMN `biz_mail`          VARCHAR(128) DEFAULT NULL COMMENT '企业邮箱' AFTER `email`,
    ADD COLUMN `telephone`         VARCHAR(32)  DEFAULT NULL COMMENT '座机' AFTER `phone`,
    ADD COLUMN `direct_leader`     VARCHAR(255) DEFAULT NULL COMMENT '直属上级（外部userid/用户名，多个逗号分隔）' AFTER `telephone`,
    ADD COLUMN `external_position` VARCHAR(128) DEFAULT NULL COMMENT '对外职务' AFTER `direct_leader`,
    ADD COLUMN `external_nickname` VARCHAR(64)  DEFAULT NULL COMMENT '对外昵称' AFTER `external_position`,
    ADD COLUMN `address`           VARCHAR(128) DEFAULT NULL COMMENT '地址' AFTER `external_nickname`,
    ADD COLUMN `ext_attr`          TEXT         COMMENT '扩展属性（JSON）' AFTER `address`,
    ADD KEY `idx_employee_no` (`employee_no`);

CREATE TABLE IF NOT EXISTS `sys_user_dept` (
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

-- 存量数据回填：原 dept_id 作为主部门
INSERT INTO `sys_user_dept` (`user_id`, `dept_id`, `is_main`, `is_leader`, `order_in_dept`, `create_time`, `update_time`)
SELECT `id`, `dept_id`, 1, 0, 0, NOW(), NOW()
FROM `sys_user`
WHERE `dept_id` IS NOT NULL AND `del_flag` = 0
ON DUPLICATE KEY UPDATE `is_main` = 1;
