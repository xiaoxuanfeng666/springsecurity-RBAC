-- =============================================================================
-- 增量升级：移除「SSO 会话」管理页与相关权限点
-- 日期：2026-10-17
-- 目标库：rbac
-- =============================================================================
-- 背景：rbac-backend 已不再提供 SSO 相关接口（/sso/session、/portal/**），
--       前端对应的「SSO 会话」页面也已删除。这里把当初为它加的菜单与授权清掉，
--       避免后台导航里留下一个点进去 404 的入口。
--
-- 删除顺序：先删角色授权，再删菜单 —— 否则会留下指向不存在菜单的孤儿授权行。
-- 关联的增量脚本：sql/upgrade-2026-10-16-sso.sql 的第 ③④ 段已同步移除。
-- =============================================================================

USE `rbac`;

-- 按钮（会话查询 / 强制下线）
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (1095, 1096);
DELETE FROM `sys_menu`      WHERE `id` IN (1095, 1096);

-- 页面节点
DELETE FROM `sys_role_menu` WHERE `menu_id` = 110;
DELETE FROM `sys_menu`      WHERE `id` = 110;

-- 校验：两条都应为 0
-- SELECT COUNT(*) FROM sys_menu WHERE id IN (110, 1095, 1096);
-- SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (110, 1095, 1096);
