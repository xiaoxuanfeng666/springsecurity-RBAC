-- =============================================================================
-- 增量升级：SSO 客户端配置（统一登出 / 子系统静默换令牌）
-- 日期：2026-10-16（2026-10-17 修订：移除「SSO 会话」菜单部分）
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 前置：已执行过 upgrade-2026-10-16-order-center.sql 与 upgrade-2026-10-16-marketing.sql
--       （订单中心 / 营销系统两个子系统与它们的 OAuth2 客户端都已就位）
--
-- 本脚本做两件事：
--   ① 新增 OAuth2 客户端 portal（统一门户 5177 的客户端注册；门户前端仍保留，
--      但它的后端接口已从 rbac-backend 移除，见 upgrade-2026-10-17-remove-sso-menu.sql）
--   ② 给 order-center / marketing 两个客户端打开「免确认页」——
--      已登录过的人再进另一个子系统时不弹确认页，凭会话直接发码
--
-- 历史上的 ③④ 段（「SSO 会话」菜单与它的角色授权）已随 rbac-backend 的相关接口一并移除，
-- 旧库请执行 upgrade-2026-10-17-remove-sso-menu.sql 清理。
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
-- 两个子系统申请的 scope 都是 read（见各自 application.yml 的 oauth2.scope），所以放行 read：
-- 已经登录过的人再进另一个子系统时，授权端点凭会话直接发码，不弹确认页。
UPDATE `clientdetails` SET `autoApproveScopes` = 'read'
WHERE `appId` IN ('order-center', 'marketing');

-- 校验：
-- SELECT appId, scope, grantTypes, redirectUrl, autoApproveScopes FROM clientdetails ORDER BY appId;
--   应看到 4 个客户端，其中 order-center / marketing 的 autoApproveScopes='read'。

-- 【执行后必做】重启 rbac-oauth，新的客户端配置与 SSO 令牌服务才会生效。
-- 会话相关的 Redis 键全部由 rbac-oauth 自己维护，无需手工初始化：
--   sso:session:{username}  —— SSO 会话（存在=令牌可用），TTL 7 天
--   sso:browser:{username}  —— 该用户浏览器登录态的 token
--   oauth2:auth:{token}     —— 登录态本体（精简 JSON，只含认证信息）
