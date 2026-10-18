-- =============================================================================
-- 增量升级：移除「子系统内授权」功能
-- 日期：2026-10-18
-- 目标库：rbac / order_center / marketing / audit_log
-- =============================================================================
-- 取消子系统管理员与子系统内授权页，恢复到「只在 RBAC 的角色管理里分配子系统菜单」。
-- 删除：
--   ① RBAC 的三个子系统管理员权限点（1111/1112/1113）及其角色授权；
--   ② 各子系统用户表的 subsystem_admin 标记；
--   ③ 早期版本可能遗留的用户直接授权表（IF EXISTS）。
-- =============================================================================

USE `rbac`;

DELETE FROM `sys_role_menu` WHERE `menu_id` IN (1111, 1112, 1113);
DELETE FROM `sys_menu` WHERE `id` IN (1111, 1112, 1113);
DROP TABLE IF EXISTS `sys_user_subsystem_menu`;

USE `order_center`;
ALTER TABLE `order_user` DROP COLUMN `subsystem_admin`;
DROP TABLE IF EXISTS `order_user_menu`;

USE `marketing`;
ALTER TABLE `marketing_user` DROP COLUMN `subsystem_admin`;
DROP TABLE IF EXISTS `marketing_user_menu`;

USE `audit_log`;
ALTER TABLE `audit_user` DROP COLUMN `subsystem_admin`;
DROP TABLE IF EXISTS `audit_user_menu`;
