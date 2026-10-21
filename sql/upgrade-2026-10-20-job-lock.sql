-- =============================================================================
-- 增量升级：定时任务分布式锁表
-- 日期：2026-10-20
-- 目标库：rbac
-- =============================================================================
-- 背景：
--   rbac-oauth 可多实例部署，而 @Scheduled 在每个实例上都会触发。会话对账任务
--   （SsoSessionRecordService#reconcile）用本表的一行做「租约式行锁」选举：
--   只有把 locked_until 从过期状态原子更新成功的实例才执行本轮。
--
--   锁不显式释放，到期时间 = 当前时间 + 55 秒（略小于 60 秒调度周期），
--   实例崩溃后最多一个周期即被其他实例接管。
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_job_lock` (
    `job_name`     VARCHAR(64)  NOT NULL COMMENT '任务名（每个任务一行）',
    `locked_until` DATETIME     DEFAULT NULL COMMENT '租约到期时间，NULL 表示未加锁',
    `holder`       VARCHAR(128) DEFAULT NULL COMMENT '持锁实例标识，便于排查',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '最后更新时间',
    PRIMARY KEY (`job_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='定时任务分布式锁（租约）';

INSERT IGNORE INTO `sys_job_lock` (`job_name`, `locked_until`, `holder`, `update_time`)
VALUES ('sso-session-reconcile', NULL, NULL, NOW());

-- 校验：
-- SELECT * FROM sys_job_lock;
