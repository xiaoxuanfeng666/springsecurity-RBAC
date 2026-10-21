-- =============================================================================
-- 增量升级：补齐用户管理的细粒度按钮权限 + 会话管理按钮
-- 日期：2026-10-20
-- 目标库：rbac
-- =============================================================================
-- 背景：
--   用户管理页面的「停用/启用」「离职/复职」「身份绑定」「推送外部」原先共用
--   system:user:edit，无法按角色单独授权。这里拆成独立权限点：
--     system:user:status   停用/启用
--     system:user:resign   离职/复职
--     system:user:identity 外部身份绑定/解绑
--     system:user:sync     推送外部通讯录（企业微信/钉钉）
--
--   同时在部分环境里补齐会话管理的「强制下线」按钮（system:sso:kick）。
--
-- 说明：
--   INSERT IGNORE 以主键去重，脚本可重复执行；超级管理员权限不落库，无需授权。
-- =============================================================================

USE `rbac`;

-- 用户管理：细粒度按钮
INSERT IGNORE INTO `sys_menu`
    (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1007, 100, '用户停用/启用', NULL, NULL, 'system:user:status', 3, NULL, 7, 1, 1, NOW(), NOW()),
       (1008, 100, '用户离职/复职', NULL, NULL, 'system:user:resign', 3, NULL, 8, 1, 1, NOW(), NOW()),
       (1009, 100, '身份绑定', NULL, NULL, 'system:user:identity', 3, NULL, 9, 1, 1, NOW(), NOW()),
       (1010, 100, '推送外部用户', NULL, NULL, 'system:user:sync', 3, NULL, 10, 1, 1, NOW(), NOW());

-- 会话管理：菜单与按钮（已在升级脚本中的环境会被 IGNORE 跳过）
INSERT IGNORE INTO `sys_menu`
    (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (110, 1, 'SSO 会话', 'sso-session', 'system/sso-session/index', 'system:sso:list', 2, 'Connection', 10, 1, 1, NOW(), NOW()),
       (1095, 110, '会话查询', NULL, NULL, 'system:sso:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1096, 110, '强制下线', NULL, NULL, 'system:sso:kick', 3, NULL, 2, 1, 1, NOW(), NOW());

-- 向后兼容：原先这些按钮都挂在「用户修改」上，已获得该权限的角色一并继承新权限点，
-- 避免升级后普通角色突然无法停用/绑定/推送。之后可在「角色管理」里按需收回。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT `rm`.`role_id`, `m`.`id`
FROM `sys_role_menu` `rm`
         JOIN `sys_menu` `m` ON `m`.`id` IN (1007, 1008, 1009, 1010)
WHERE `rm`.`menu_id` = 1003;

-- 校验：
-- SELECT id, parent_id, menu_name, perms FROM sys_menu WHERE id IN (1007,1008,1009,1010,110,1095,1096) ORDER BY id;
