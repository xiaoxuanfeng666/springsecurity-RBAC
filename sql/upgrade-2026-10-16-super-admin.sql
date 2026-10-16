-- ============================================================
-- 增量升级：超级管理员权限改为代码隐式授予
-- 适用于已经给 admin 角色配过 sys_role_menu 的旧库（早先的 data.sql 里有
--   INSERT INTO sys_role_menu SELECT 1, id FROM sys_menu;
-- 新增菜单时还得手动给 admin 补授权，很麻烦）。
--
-- 现在超级管理员的权限**不落库**：rbac-backend 的 security/SuperAdmin 在构造权限快照时，
-- 直接给通配符 *:*:* 让所有 hasPerm 放行；/auth/info 也直接返回全部启用菜单。
-- 所以 admin 在 sys_role_menu 里的行都是多余的，本脚本把它们清掉。
--
-- 本脚本只做 DELETE，**可重复执行**。
--
-- 【执行后必做】清理 Redis 里的权限快照。删掉的是「权限的来源」，但已登录会话的快照还是旧的
-- （旧快照里是一长串具体权限，缺新菜单）。清掉后下一次请求会重建为通配符版本：
--   redis-cli EVAL "local ks=redis.call('keys','login:perm:*'); for i,k in ipairs(ks) do redis.call('del',k) end; return #ks" 0
-- ============================================================

USE `rbac`;

-- 删掉超级管理员角色的全部菜单授权（按 role_key 定位，不依赖 id 刚好是 1）
DELETE FROM `sys_role_menu`
WHERE `role_id` IN (SELECT `id` FROM `sys_role` WHERE `role_key` = 'admin');

-- 子系统菜单的关联记录同理。该表由 upgrade-2026-10-16-subsystem.sql 创建，
-- 若本脚本先于它执行则表还不存在，这里做了判断，两种顺序都能跑。
SET @has_tbl := (SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'rbac' AND table_name = 'sys_role_subsystem_menu');
SET @stmt := IF(@has_tbl > 0,
    'DELETE FROM sys_role_subsystem_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_key = ''admin'')',
    'SELECT ''sys_role_subsystem_menu 尚不存在，跳过'' AS note');
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 核对：下面两条都应返回 0 行
--   SELECT rm.* FROM sys_role_menu rm
--   JOIN sys_role r ON rm.role_id = r.id WHERE r.role_key = 'admin';
--   SELECT rm.* FROM sys_role_subsystem_menu rm
--   JOIN sys_role r ON rm.role_id = r.id WHERE r.role_key = 'admin';
