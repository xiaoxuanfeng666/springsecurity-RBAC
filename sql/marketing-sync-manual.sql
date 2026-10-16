-- =============================================================================
-- 营销系统：手工灌一次同步数据（**仅用于 RocketMQ 未启用时联调**）
-- =============================================================================
-- 正常情况下这四张表的内容由 RBAC 经 RocketMQ 下发（topic RBAC_SUBSYSTEM_SYNC，
-- tag=marketing），本系统收到消息后清空重建。本脚本只是把「RBAC 现在会发什么」
-- 用一条跨库 SQL 直接搬过来，方便在没有 MQ 的环境里验证登录、菜单与权限拦截。
--
-- 【重要】这是**绕过 MQ 的手工兜底**：
--   · 执行后 marketing 库里的数据与走一遍 MQ 完全等价；
--   · 但它不会自动更新 —— RBAC 侧改了菜单/授权，要重跑本脚本才生效；
--   · RocketMQ 一旦跑起来，下一次下发就会把手工灌的数据整体覆盖掉。
--
-- 与生产者的对应关系（见 rbac-backend 的 SubsystemSyncService.build）：
--   users       ← sys_user（只在职）+ 该用户的角色标识（只算启用且未删除的角色）
--   menus       ← sys_subsystem_menu（该子系统全部菜单，含停用的）
--   roleMenus   ← sys_role_subsystem_menu（只保留启用且未删除的角色）
--   userRoles   ← sys_user_role（只保留启用且未删除的角色）
--
-- 【注意】营销库必须已存在（先执行 marketing-schema.sql）。
-- =============================================================================

-- ---------- 用户 ----------
DELETE FROM `marketing`.`marketing_user`;
INSERT INTO `marketing`.`marketing_user` (`id`, `username`, `nickname`, `status`, `role_keys`, `sync_time`)
SELECT u.id,
       u.username,
       u.nickname,
       u.status,
       (SELECT GROUP_CONCAT(DISTINCT r.role_key)
          FROM `rbac`.`sys_user_role` ur
          JOIN `rbac`.`sys_role` r ON r.id = ur.role_id
         WHERE ur.user_id = u.id AND r.status = 1 AND r.del_flag = 0),
       NOW()
FROM `rbac`.`sys_user` u
WHERE u.status = 1 AND u.del_flag = 0;

-- ---------- 菜单与权限点 ----------
DELETE FROM `marketing`.`marketing_menu`;
INSERT INTO `marketing`.`marketing_menu` (`id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `sync_time`)
SELECT sm.id, sm.parent_id, sm.menu_name, sm.type, sm.perms, sm.path, sm.component,
       sm.icon, sm.sort, sm.visible, sm.status, NOW()
FROM `rbac`.`sys_subsystem_menu` sm
JOIN `rbac`.`sys_subsystem` s ON s.id = sm.subsystem_id
WHERE s.subsystem_code = 'marketing' AND s.del_flag = 0;

-- ---------- 角色-菜单 ----------
DELETE FROM `marketing`.`marketing_role_menu`;
INSERT INTO `marketing`.`marketing_role_menu` (`role_id`, `menu_id`, `sync_time`)
SELECT rsm.role_id, rsm.subsystem_menu_id, NOW()
FROM `rbac`.`sys_role_subsystem_menu` rsm
JOIN `rbac`.`sys_subsystem_menu` sm ON sm.id = rsm.subsystem_menu_id
JOIN `rbac`.`sys_subsystem` s ON s.id = sm.subsystem_id
JOIN `rbac`.`sys_role` r ON r.id = rsm.role_id
WHERE s.subsystem_code = 'marketing' AND s.del_flag = 0
  AND r.status = 1 AND r.del_flag = 0;

-- ---------- 用户-角色 ----------
DELETE FROM `marketing`.`marketing_user_role`;
INSERT INTO `marketing`.`marketing_user_role` (`user_id`, `role_id`, `sync_time`)
SELECT ur.user_id, ur.role_id, NOW()
FROM `rbac`.`sys_user_role` ur
JOIN `rbac`.`sys_role` r ON r.id = ur.role_id
WHERE r.status = 1 AND r.del_flag = 0;

-- 校验：应看到用户在 2 个左右、菜单 19 条、角色菜单 4 条、用户角色 2 条
-- SELECT (SELECT COUNT(*) FROM marketing.marketing_user)       AS users,
--        (SELECT COUNT(*) FROM marketing.marketing_menu)       AS menus,
--        (SELECT COUNT(*) FROM marketing.marketing_role_menu)  AS role_menus,
--        (SELECT COUNT(*) FROM marketing.marketing_user_role)  AS user_roles;
