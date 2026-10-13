-- ============================================================
-- 增量升级脚本：分公司并入部门树 + 新增岗位模块
-- 适用：已应用过 upgrade-2026-10-13-company.sql 的数据库
-- 说明：本脚本一次性执行，重复执行会因 ALTER 报错
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < upgrade-2026-10-13-org-post.sql
-- ============================================================

USE `rbac`;

-- ---------- 1. 部门表新增类型字段 ----------
-- type：1公司 2分公司 3部门
ALTER TABLE `sys_dept`
    ADD COLUMN `type` TINYINT NOT NULL DEFAULT 3 COMMENT '类型：1公司 2分公司 3部门' AFTER `dept_name`;

-- ---------- 2. 分公司数据迁入部门树，挂在总公司下作为 type=2 节点 ----------
INSERT INTO `sys_dept` (`parent_id`, `dept_name`, `type`, `sort`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_time`, `update_time`)
SELECT 1, `company_name`, 2, `sort`, `leader`, `phone`, `email`, `status`, `del_flag`, NOW(), NOW()
FROM `sys_company`
WHERE `del_flag` = 0;

-- ---------- 3. 原部门按 company_id 挂到对应分公司节点下 ----------
UPDATE `sys_dept` d
    JOIN `sys_company` c ON d.`company_id` = c.`id`
    JOIN `sys_dept` nc ON nc.`dept_name` = c.`company_name` AND nc.`type` = 2
SET d.`parent_id` = nc.`id`
WHERE d.`company_id` IS NOT NULL;

-- ---------- 4. 顶级节点标记为公司 ----------
UPDATE `sys_dept` SET `type` = 1 WHERE `parent_id` = 0;

-- ---------- 5. 去掉冗余的 company_id，删除分公司表 ----------
ALTER TABLE `sys_dept` DROP KEY `idx_dept_company_id`, DROP COLUMN `company_id`;
ALTER TABLE `sys_user` DROP KEY `idx_company_id`, DROP COLUMN `company_id`;
DROP TABLE IF EXISTS `sys_company`;

-- ---------- 6. 下线「分公司管理」菜单 ----------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (104, 1041, 1042, 1043, 1044);
DELETE FROM `sys_menu` WHERE `id` IN (104, 1041, 1042, 1043, 1044);

-- ---------- 7. 岗位表与用户-岗位关联表 ----------
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

DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post` (
    `id`      BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `post_id` BIGINT NOT NULL COMMENT '岗位ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户岗位关联表';

-- ---------- 8. 岗位初始数据 ----------
INSERT INTO `sys_post` (`id`, `post_code`, `post_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'ceo', '董事长', 1, 1, 0, '全局管理', NOW(), NOW()),
       (2, 'pm', '项目经理', 2, 1, 0, '项目负责人', NOW(), NOW()),
       (3, 'hr', '人力资源', 3, 1, 0, '人事相关', NOW(), NOW()),
       (4, 'staff', '普通员工', 4, 1, 0, '默认岗位', NOW(), NOW());

INSERT INTO `sys_user_post` (`user_id`, `post_id`)
SELECT 1, 1 WHERE EXISTS (SELECT 1 FROM `sys_user` WHERE `id` = 1);
INSERT INTO `sys_user_post` (`user_id`, `post_id`)
SELECT 2, 4 WHERE EXISTS (SELECT 1 FROM `sys_user` WHERE `id` = 2);

-- ---------- 9. 岗位菜单与按钮权限 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (105, 1, '岗位管理', 'post', 'system/post/index', 'system:post:list', 2, 'Postcard', 5, 1, 1, NOW(), NOW()),
       (1051, 105, '岗位查询', NULL, NULL, 'system:post:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1052, 105, '岗位新增', NULL, NULL, 'system:post:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1053, 105, '岗位修改', NULL, NULL, 'system:post:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1054, 105, '岗位删除', NULL, NULL, 'system:post:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `sys_menu` WHERE `id` IN (105, 1051, 1052, 1053, 1054);
