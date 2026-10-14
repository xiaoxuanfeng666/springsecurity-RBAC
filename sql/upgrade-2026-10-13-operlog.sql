-- ============================================================
-- 增量升级：审计日志扩展为通用操作日志
-- 适用于已执行过 upgrade-2026-10-13-audit.sql 的库（表名 sys_login_audit）
-- 变更内容：表改名、新增模块/说明/请求方法/路径/结果/耗时列，原 reason 并入 description
-- 注意：本脚本一次性执行，重复执行会因表已改名而报错
-- 本次未改菜单与权限标识，因此不需要清理权限快照
-- ============================================================

USE `rbac`;

-- 改名并保留现有数据
RENAME TABLE `sys_login_audit` TO `sys_operation_log`;

ALTER TABLE `sys_operation_log`
    ADD COLUMN `module`      VARCHAR(64)  DEFAULT NULL COMMENT '模块，如 用户管理' AFTER `id`,
    ADD COLUMN `description` VARCHAR(128) DEFAULT NULL COMMENT '操作说明' AFTER `action`,
    ADD COLUMN `http_method` VARCHAR(10)  DEFAULT NULL COMMENT '请求方法' AFTER `operator_name`,
    ADD COLUMN `request_uri` VARCHAR(255) DEFAULT NULL COMMENT '请求路径' AFTER `http_method`,
    ADD COLUMN `success`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1成功 0失败' AFTER `request_uri`,
    ADD COLUMN `error_msg`   VARCHAR(255) DEFAULT NULL COMMENT '失败原因' AFTER `success`,
    ADD COLUMN `duration_ms` BIGINT       DEFAULT NULL COMMENT '耗时（毫秒）' AFTER `error_msg`,
    MODIFY COLUMN `action`   VARCHAR(32)  NOT NULL
        COMMENT '动作：LOGIN/LOGOUT/KICK/DISABLED/ENABLED/RESIGNED/RESET_PWD/CHANGE_PWD/CREATE/UPDATE/DELETE/GRANT';

-- 原有 reason 语义并入 description，随后删列
UPDATE `sys_operation_log` SET `description` = `reason` WHERE `description` IS NULL;
ALTER TABLE `sys_operation_log` DROP COLUMN `reason`;

-- 回填历史记录的模块（旧数据只有登录/下线类动作，没有模块概念）
UPDATE `sys_operation_log` SET `module` = '认证'
    WHERE `module` IS NULL AND `action` IN ('LOGIN', 'LOGOUT', 'KICK', 'CHANGE_PWD');
UPDATE `sys_operation_log` SET `module` = '用户管理'
    WHERE `module` IS NULL AND `action` IN ('DISABLED', 'ENABLED', 'RESIGNED', 'RESET_PWD');
