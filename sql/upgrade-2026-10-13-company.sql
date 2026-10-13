-- ============================================================
-- 增量升级脚本：新增「分公司」模块
-- 适用：已初始化过、且已应用 upgrade-2026-10-13-dept.sql 的数据库
-- 说明：本脚本一次性执行，重复执行会因 ALTER 报错
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < upgrade-2026-10-13-company.sql
-- ============================================================

USE `rbac`;

-- ---------- 1. 分公司表 ----------
DROP TABLE IF EXISTS `sys_company`;
CREATE TABLE `sys_company` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分公司ID',
    `company_name` VARCHAR(128) NOT NULL COMMENT '分公司名称',
    `company_code` VARCHAR(64)  DEFAULT NULL COMMENT '分公司编码',
    `leader`       VARCHAR(64)  DEFAULT NULL COMMENT '负责人',
    `phone`        VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `email`        VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `address`      VARCHAR(255) DEFAULT NULL COMMENT '地址',
    `sort`         INT          NOT NULL DEFAULT 0 COMMENT '显示顺序',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `del_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    `create_time`  DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_company_code` (`company_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='分公司表';

-- ---------- 2. 部门 / 用户 增加分公司归属 ----------
ALTER TABLE `sys_dept`
    ADD COLUMN `company_id` BIGINT DEFAULT NULL COMMENT '所属分公司ID' AFTER `parent_id`,
    ADD KEY `idx_dept_company_id` (`company_id`);

ALTER TABLE `sys_user`
    ADD COLUMN `company_id` BIGINT DEFAULT NULL COMMENT '所属分公司ID' AFTER `dept_id`,
    ADD KEY `idx_company_id` (`company_id`);

-- ---------- 3. 分公司初始数据 ----------
INSERT INTO `sys_company` (`id`, `company_name`, `company_code`, `leader`, `phone`, `email`, `address`, `sort`, `status`, `del_flag`, `create_time`, `update_time`)
VALUES (1, '北京分公司', 'BJ', '李四', '13800000010', 'bj@example.com', '北京市海淀区', 1, 1, 0, NOW(), NOW()),
       (2, '上海分公司', 'SH', '王五', '13800000020', 'sh@example.com', '上海市浦东新区', 2, 1, 0, NOW(), NOW());

-- ---------- 4. 已有部门 / 用户归属分公司 ----------
-- 总公司作为集团顶级节点不归属具体分公司
UPDATE `sys_dept` SET `company_id` = 1 WHERE `id` IN (2, 4, 5);   -- 研发部 / 前端组 / 后端组 -> 北京
UPDATE `sys_dept` SET `company_id` = 2 WHERE `id` = 3;            -- 市场部 -> 上海
UPDATE `sys_user` SET `company_id` = 1 WHERE `username` = 'zhangsan';

-- ---------- 5. 分公司菜单与按钮权限 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (104, 1, '分公司管理', 'company', 'system/company/index', 'system:company:list', 2, 'Suitcase', 5, 1, 1, NOW(), NOW()),
       (1041, 104, '分公司查询', NULL, NULL, 'system:company:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1042, 104, '分公司新增', NULL, NULL, 'system:company:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1043, 104, '分公司修改', NULL, NULL, 'system:company:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1044, 104, '分公司删除', NULL, NULL, 'system:company:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- 6. 授权给 admin 角色 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (104, 1041, 1042, 1043, 1044);
