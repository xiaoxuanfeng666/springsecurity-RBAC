-- ============================================================
-- 增量升级：登录/下线审计日志
-- 适用于已按 schema.sql 初始化过的旧库；全新安装直接用 schema.sql + data.sql 即可
-- 注意：本脚本一次性执行，重复执行会因 CREATE TABLE 已存在而报错
--
-- 【执行后必做】清理 Redis 中的权限快照，否则已登录用户（含 admin）读到的仍是登录时的旧快照，
-- 看不到新菜单，必须重新登录才行。快照只是缓存，清掉不会把人踢下线，下一次请求会自动重建：
--   redis-cli EVAL "local ks=redis.call('keys','login:perm:*'); for i,k in ipairs(ks) do redis.call('del',k) end; return #ks" 0
-- ============================================================

USE `rbac`;

-- ---------- 审计日志表 ----------
CREATE TABLE `sys_login_audit` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '目标用户ID',
    `username`      VARCHAR(64)  DEFAULT NULL COMMENT '目标用户名',
    `action`        VARCHAR(32)  NOT NULL COMMENT '动作：LOGIN/LOGOUT/KICK/DISABLED/RESIGNED/RESET_PWD',
    `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人ID（本人操作时与目标一致）',
    `operator_name` VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
    `reason`        VARCHAR(255) DEFAULT NULL COMMENT '说明',
    `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='登录/下线审计日志';

-- ---------- 菜单：操作日志页面 ----------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (106, 1, '操作日志', 'audit', 'system/audit/index', 'system:audit:list', 2, 'Document', 6, 1, 1, NOW(), NOW());

-- ---------- admin 角色授予该菜单（否则 admin 也看不到日志）----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1, 106);
