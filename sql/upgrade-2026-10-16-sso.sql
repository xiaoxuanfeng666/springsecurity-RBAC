-- =============================================================================
-- 增量升级：企业级统一门户 SSO（单点登录 + 统一登出 + 会话管理）
-- 日期：2026-10-16
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 前置：已执行过 upgrade-2026-10-16-order-center.sql 与 upgrade-2026-10-16-marketing.sql
--       （订单中心 / 营销系统两个子系统与它们的 OAuth2 客户端都已就位）
--
-- 本脚本做四件事：
--   ① 新增 OAuth2 客户端 portal（统一门户 5177 用它做授权码登录）
--   ② 给 order-center / marketing 两个客户端打开「免确认页」，门户一键登录才可能静默完成
--   ③ 新增「SSO 会话」菜单与权限点（会话查询 / 强制下线）
--   ④ 把新页面授给普通角色（只读，不给强制下线按钮）
--
-- 【注意】本脚本含 INSERT（固定主键）与 UPDATE，只能执行一次。
-- =============================================================================

USE `rbac`;

-- ---------- ① 统一门户的 OAuth2 客户端 ----------
-- appSecret 是 'portal123' 的 AES/GCM 密文（密钥同 oauth2.client-secret-key）。
-- 与子系统客户端的关键差别：
--   * 不给 password 模式 —— 门户只走授权码，用户必须在授权服务的登录页认证，
--     这样浏览器里才会有 SESSION（一键登录其余子系统正是靠这个 SESSION 免去二次登录）；
--   * 不给 client_credentials —— 门户代表的是人，不是机器身份。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`,
                             `authorities`, `access_token_validity`, `refresh_token_validity`,
                             `additionalInformation`, `autoApproveScopes`)
VALUES ('portal', NULL, 'Rxo21XV1MhryGGFp6DCuXtwIU2fEF3RVmNimrWfS53BrYqubCg==',
        'read', 'authorization_code,refresh_token',
        'http://localhost:5177/oauth/callback',
        'ROLE_CLIENT', 3600, 604800, NULL, 'read');

-- ---------- ② 子系统客户端免确认页 ----------
-- autoApproveScopes 里的范围在授权时跳过「确认授权」页，直接把 code 发回 redirect_uri。
-- 门户的「一键登录所有子系统」是在隐藏 iframe 里跑授权码流程的：
-- 用户看不见那个页面，一旦弹出确认页，就卡在那里等一个永远等不到的点击。
-- 两个子系统申请的 scope 都是 read（见各自 application.yml 的 oauth2.scope），所以放行 read 即可。
UPDATE `clientdetails` SET `autoApproveScopes` = 'read'
WHERE `appId` IN ('order-center', 'marketing');

-- ---------- ③ SSO 会话菜单与权限点 ----------
-- 页面节点（type=2）的 perms 是「能进这个页面」的凭据；
-- 按钮（type=3）的 perms 才是接口鉴权用的，接口靠 @PreAuthorize("@ss.hasPerm('system:sso:list')") 判定。
-- 主键从 110 起（页面）、1095 起（按钮），与已有分段错开。
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (110, 1, 'SSO 会话', 'sso-session', 'system/sso-session/index', 'system:sso:list', 2, 'Connection', 10, 1, 1, NOW(), NOW());

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1095, 110, '会话查询', NULL, NULL, 'system:sso:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1096, 110, '强制下线', NULL, NULL, 'system:sso:kick', 3, NULL, 2, 1, 1, NOW(), NOW());

-- ---------- ④ 普通角色的授权 ----------
-- 只给「能看」：普通用户能看到自己或同事在线，但没有「强制下线」按钮，直接调接口会 403。
-- 超级管理员(admin)不在这里授权：它的权限由 SuperAdmin 类隐式给全集。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (2, 110),   -- SSO 会话（页面）
       (2, 1095);  -- 会话查询

-- 校验：
-- SELECT appId, scope, grantTypes, redirectUrl, autoApproveScopes FROM clientdetails ORDER BY appId;
--   应看到 4 个客户端，其中 portal 只有 authorization_code,refresh_token，
--   order-center / marketing 的 autoApproveScopes='read'。
-- SELECT id, menu_name, type, perms FROM sys_menu WHERE id IN (110, 1095, 1096);
-- SELECT role_id, menu_id FROM sys_role_menu WHERE role_id = 2;

-- 【执行后必做】重启 rbac-oauth，新的客户端配置与 SSO 令牌服务才会生效。
-- 会话相关的 Redis 键由 rbac-oauth 写入、rbac-backend 读取，无需手工初始化：
--   sso:session:{username}  —— SSO 会话（存在=令牌可用），TTL 7 天
--   sso:browser:{username}  —— 浏览器会话索引，供「强制下线」销毁对方的 SESSION
