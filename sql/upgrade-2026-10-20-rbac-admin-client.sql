-- =============================================================================
-- 增量升级：RBAC 管理后台接入统一 SSO（rbac-admin 客户端）
-- 日期：2026-10-20
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 背景：
--   rbac-backend（7070，管理后台）已放弃自签 JWT，登录授权全部交给 rbac-oauth。
--   浏览器流程：5173 未登录 → GET 7070/auth/sso-login（302）→ 5174 登录 →
--   回调 5173/oauth/callback → POST 7070/auth/code 换令牌 → 之后每个请求
--   由 rbac-backend 调 7071/oauth/check_token 校验。
--
-- 本脚本做一件事：登记授权码模式客户端 rbac-admin。
--   appSecret 是 'rbac123' 的 AES/GCM 密文（密钥同 oauth2.client-secret-key）。
--   redirectUrl 必须与 rbac-backend 的 oauth2.redirect-uri 完全一致，否则换令牌会被拒。
--   grantTypes 只保留 authorization_code / refresh_token：后台不再提供密码登录。
--   autoApproveScopes=read：该客户端只申请 read，授权页无需用户确认，登录后直接回调。
--
-- 【注意】本脚本含 INSERT，只能执行一次（appId 是主键，重复执行会报重复键）。
-- =============================================================================

USE `rbac`;

INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`,
                             `authorities`, `access_token_validity`, `refresh_token_validity`,
                             `additionalInformation`, `autoApproveScopes`)
VALUES ('rbac-admin', NULL, 'LZUXslbxKjUDNjSg45x+khzZHW/BvAWWOvLvUAzPo5oHuv0=',
        'read', 'authorization_code,refresh_token',
        'http://localhost:5173/oauth/callback',
        'ROLE_CLIENT', 3600, 604800, NULL, 'read');

-- 校验：应看到一行 rbac-admin
-- SELECT appId, scope, grantTypes, redirectUrl, autoApproveScopes FROM clientdetails WHERE appId = 'rbac-admin';

-- =============================================================================
-- 旧登录态清理（可选）
-- 改造前 rbac-backend 用自己的 Redis 会话：
--   login:session:{userId} / login:refresh:{sessionId} / login:perm:{sessionId}
-- 改造后只保留 login:perm:user:{username}（权限快照），前两种不再写入，
-- 旧快照也没有任何请求会读到。可等 TTL 自然过期，或手动清掉：
--
--   redis-cli --scan --pattern 'login:session:*' | xargs -r redis-cli del
--   redis-cli --scan --pattern 'login:refresh:*' | xargs -r redis-cli del
--   redis-cli --scan --pattern 'login:perm:*'    | xargs -r redis-cli del
--
-- 清掉不会把人踢下线：新的判定依据是 rbac-oauth 的 sso:session:{username}，
-- 下一次请求会按需重建权限快照。
-- =============================================================================
