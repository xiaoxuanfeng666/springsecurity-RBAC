-- =============================================================================
-- 增量升级：Access Token 有效期 1 小时 → 30 分钟
-- 日期：2026-10-20
-- 目标库：rbac
-- =============================================================================
-- 背景：
--   子系统改为本地验签后，撤销依赖 MQ 推送 + Redis 撤销集合。MQ 丢失时的最坏暴露
--   窗口等于「令牌剩余有效期」，把有效期从 3600 秒收到 1800 秒可让窗口减半。
--   Refresh Token 仍为 7 天，前端 401 自动刷新，用户无感知。
--
--   撤销记录 TTL 由 rbac-oauth 按客户端有效期实时计算（随消息下发），改完自动跟随，
--   不需要调整 rbac.session.revoke-ttl-seconds（那只是消息缺字段时的兜底值）。
--
-- 【重要】执行后必须清掉 rbac-oauth 的客户端配置缓存，否则最多 1 小时内仍按旧有效期签发：
--   redis-cli --scan --pattern 'oauth2:client:*' | xargs -r redis-cli del
-- =============================================================================

USE `rbac`;

UPDATE `clientdetails`
SET `access_token_validity` = 1800;

-- 校验：
-- SELECT appId, access_token_validity, refresh_token_validity FROM clientdetails ORDER BY appId;
