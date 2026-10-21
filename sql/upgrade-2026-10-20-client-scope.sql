-- =============================================================================
-- 增量升级：clientdetails.scope 不允许为空
-- 日期：2026-10-20
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 背景：
--   scope 是客户端的授权范围上限，也是授权请求不传 scope 时的默认授予
--   （spring-security-oauth2 的 DefaultOAuth2RequestFactory.extractScopes）。
--   更关键的是 DefaultOAuth2RequestValidator 的行为：只有 clientdetails.scope 非空时
--   才逐个校验请求的 scope；scope 为空会整段跳过 —— 也就是说空 scope 不是「最严格」，
--   而是「申请什么都放行」。因此必须在写入侧和读取侧都杜绝空值。
--
--   rank 对应改动：
--     * rbac-backend  ClientForm.scope 加 @NotBlank（管理端新增/编辑时拦截）
--     * rbac-oauth    MybatisClientDetailsService 加载时校验 scope 非空（挡住旧数据/脚本写入）
--     * 本脚本        数据库层最后兜底：NOT NULL + CHECK（NOT NULL 允许空字符串）
--
-- 【执行前】先确认没有空 scope 的客户端，有的话先去「客户端管理」补齐：
--   SELECT appId FROM clientdetails WHERE scope IS NULL OR TRIM(scope) = '';
-- =============================================================================

USE `rbac`;

ALTER TABLE `clientdetails`
    MODIFY `scope` VARCHAR(255) NOT NULL COMMENT '授权范围，逗号分隔（不允许为空：空 scope 会让授权校验跳过范围检查）';

-- NOT NULL 仍允许空字符串，再补一道 CHECK（MySQL 8.0.16+ 才真正强制执行）
ALTER TABLE `clientdetails`
    ADD CONSTRAINT `chk_clientdetails_scope` CHECK (TRIM(`scope`) <> '');

-- 校验：
-- SELECT appId, scope FROM clientdetails ORDER BY appId;
-- SHOW CREATE TABLE clientdetails;
