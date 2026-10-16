-- =============================================================================
-- 增量升级：订单中心接入（RBAC 侧）
-- 日期：2026-10-16
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 前置：已执行过 upgrade-2026-10-16-subsystem.sql
--       （它建了 sys_subsystem / sys_subsystem_menu / sys_role_subsystem_menu，
--         并登记了 sys_subsystem.id=1 的『订单中心』与 5 条菜单种子）
--      已执行过 upgrade-2026-10-16-marketing.sql
--       （它给 sys_subsystem_menu 补了 perms 列并放开了 type=3 按钮，本脚本不再重复 ALTER）
--
-- 本脚本做四件事：
--   ① 新增 OAuth2 客户端 order-center（订单中心用它走密码模式/授权码模式换令牌）
--   ② 给已有的两个订单菜单补上权限标识（list）
--   ③ 补上订单的按钮权限点（增删改查）
--   ④ 去掉「客户管理」两条菜单，并给普通角色(common)授只读页面
--
-- 订单中心自己的业务库是**完全独立的** `order_center` 库，
-- 由 order-schema.sql 单独创建，与 RBAC 库没有任何表共用、没有外键关联。
-- RBAC 库这边只多出「这个子系统叫什么、有哪些菜单、哪个角色能用」的配置数据。
--
-- 【注意】本脚本含 INSERT（固定主键）与 DELETE，只能执行一次。
-- =============================================================================

USE `rbac`;

-- ---------- ① 订单中心的 OAuth2 客户端 ----------
-- appSecret 是 'order123' 的 AES/GCM 密文（密钥同 oauth2.client-secret-key）。
-- 库中只存密文，rbac-oauth 认证时解密比对；要改密钥请在 RBAC 的「客户端管理」页面重设。
-- redirectUrl 里的 5176 是订单中心前端的回调页，授权码模式换令牌时校验必须完全一致。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`,
                             `authorities`, `access_token_validity`, `refresh_token_validity`,
                             `additionalInformation`, `autoApproveScopes`)
VALUES ('order-center', NULL, 'yIBwx+lZmE1+AWObckeSg200SjEGeNXqms+f7p8BB3ZigJhE',
        'read,write', 'authorization_code,password,refresh_token',
        'http://localhost:5176/oauth/callback',
        'ROLE_CLIENT', 3600, 604800, NULL, NULL);

-- ---------- ② 已有菜单补权限标识 ----------
-- 菜单节点（type=2）自带 list 权限，是「能进这个页面」的凭据。
UPDATE `sys_subsystem_menu` SET `perms` = 'order:order:list' WHERE `id` = 2;
UPDATE `sys_subsystem_menu` SET `perms` = 'order:stat:list'  WHERE `id` = 3;

-- ---------- ③ 按钮权限点（type=3）----------
-- 挂到「订单列表」(id=2) 下，visible=0 表示不在导航里显示。
-- 权限点写在 perms 上，接口靠 @PreAuthorize("@ss.hasPerm('order:order:add')") 判定。
-- 主键从 201 起，与营销系统的 100~121 分段错开，避免将来两边撞号。
INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (201, 1, 2, '订单查询', 3, 'order:order:query',  NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
       (202, 1, 2, '订单新增', 3, 'order:order:add',    NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
       (203, 1, 2, '订单修改', 3, 'order:order:edit',   NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
       (204, 1, 2, '订单删除', 3, 'order:order:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW());

-- ---------- ④ 去掉客户管理 ----------
-- 本子系统只做订单模块，客户档案不归它管。
-- 先删授权（若有），再删菜单 —— 否则会留下指向不存在菜单的孤儿授权行。
DELETE FROM `sys_role_subsystem_menu` WHERE `subsystem_menu_id` IN (4, 5);
DELETE FROM `sys_subsystem_menu`       WHERE `id` IN (4, 5);

-- ---------- ⑤ 普通角色(common)的订单中心授权 ----------
-- 只授目录与两个页面的 list，**不带任何写按钮**：
-- 用 zhangsan 登录订单中心能看到两个菜单、能查列表与统计，
-- 但页面没有新增/修改/删除入口，直接调写接口会 403 ——
-- 这就是「权限经 MQ 下发后由子系统自己拦截」的演示。
-- 超级管理员(admin)不在这里授权：它的权限由子系统代码隐式给全集（见 SuperAdmin 类）。
INSERT INTO `sys_role_subsystem_menu` (`role_id`, `subsystem_menu_id`)
VALUES (2, 1),  -- 订单管理（目录，导航用）
       (2, 2),  -- 订单列表
       (2, 3);  -- 订单统计

-- 校验：应看到 7 条菜单（1 目录 + 2 菜单 + 4 按钮）、3 条角色授权
-- SELECT type, COUNT(*) FROM sys_subsystem_menu WHERE subsystem_id = 1 GROUP BY type;
-- SELECT COUNT(*) FROM sys_role_subsystem_menu rsm
--   JOIN sys_subsystem_menu sm ON rsm.subsystem_menu_id = sm.id WHERE sm.subsystem_id = 1;

-- 【执行后必做】若 RBAC 与订单中心都在运行，触发一次子系统数据下发，
-- 让 order_center 库的 4 张同步表拿到最新的菜单与权限点。
-- 菜单/授权/用户的任何增删改都会自动触发下发（见 SubsystemSyncService），
-- 想手动催一次最省事的办法是在 RBAC「子系统菜单管理」页面点一下保存。
