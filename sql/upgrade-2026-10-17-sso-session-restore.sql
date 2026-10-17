-- =============================================================================
-- 增量升级：SSO 登录会话表 + 恢复「SSO 会话」管理页与权限点
-- 日期：2026-10-17
-- 目标库：rbac
-- =============================================================================
-- 背景：
--   ① Redis 里的 SSO 会话只是「令牌能不能用」的开关，没有历史与设备信息。
--      这里把每次登录落一行到 sys_sso_session：
--        - rbac-oauth 在表单登录成功时写入（拿到 IP / User-Agent）；
--        - 登出时更新状态与登出时间；
--        - 管理员「强制下线」时，rbac-backend 更新状态并通过 MQ 通知 rbac-oauth 删 Redis 会话。
--   ② 本次补齐了 rbac-backend 的 /sso/session 接口与前端页面，需要把当初
--      sql/upgrade-2026-10-17-remove-sso-menu.sql 删掉的菜单与授权加回来。
--
-- 说明：建表用 IF NOT EXISTS、菜单/授权用 INSERT IGNORE，可安全重复执行；
--       菜单主键为固定值（110/1095/1096），与前端组件路径 system/sso-session/index 对应。
-- =============================================================================

USE `rbac`;

-- ---------- ① SSO 登录会话记录 ----------
-- status：1 在线 / 0 已登出 / 2 被强制下线
-- 一个用户在同一时刻最多只有一行 status=1（单会话模型：新登录会把上一行置为「被新登录顶替」）。
CREATE TABLE IF NOT EXISTS `sys_sso_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话记录ID',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
    `nickname`      VARCHAR(64)  DEFAULT NULL COMMENT '昵称（冗余，便于列表展示）',
    `login_ip`      VARCHAR(64)  DEFAULT NULL COMMENT '登录 IP',
    `user_agent`    VARCHAR(512) DEFAULT NULL COMMENT '原始 User-Agent',
    `device`        VARCHAR(128) DEFAULT NULL COMMENT '解析后的设备与浏览器，如 Chrome / Windows',
    `login_time`    DATETIME     NOT NULL COMMENT '登录时间',
    `logout_time`   DATETIME     DEFAULT NULL COMMENT '登出/下线时间',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1在线 0已登出 2被强制下线',
    `logout_reason` VARCHAR(128) DEFAULT NULL COMMENT '登出原因（主动登出/被强制下线/被新登录顶替/会话过期）',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '记录创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='SSO 登录会话记录（登录/登出与登录设备）';

-- ---------- ② SSO 会话管理菜单与权限点 ----------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (110, 1, 'SSO 会话', 'sso-session', 'system/sso-session/index', 'system:sso:list', 2, 'Connection', 10, 1, 1, NOW(), NOW());

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1095, 110, '会话查询', NULL, NULL, 'system:sso:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1096, 110, '强制下线', NULL, NULL, 'system:sso:kick', 3, NULL, 2, 1, 1, NOW(), NOW());

-- 普通角色只给「能看」：没有「强制下线」按钮（system:sso:kick），直接调接口会 403
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (2, 110),   -- SSO 会话（页面）
       (2, 1095);  -- 会话查询

-- 校验：
-- SHOW CREATE TABLE sys_sso_session;
-- SELECT id, menu_name, type, perms FROM sys_menu WHERE id IN (110, 1095, 1096);
-- SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id IN (110, 1095, 1096);
