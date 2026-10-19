-- =============================================================================
-- 增量升级：RBAC 向企业微信/钉钉推送用户与部门的 Outbox
-- 日期：2026-10-18
-- 目标库：rbac
-- =============================================================================
-- 本地用户/部门新增、修改、删除时，先在同一个事务里写一条 outbox，
-- 由后台任务调用外部通讯录 API，失败可重试，避免外部接口故障拖垮本地事务。
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_idp_outbox` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `provider`        VARCHAR(32)  NOT NULL COMMENT '目标：wecom / dingtalk / mock',
    `operation`       VARCHAR(32)  NOT NULL COMMENT 'USER_CREATE/USER_UPDATE/USER_DELETE/DEPT_CREATE/DEPT_UPDATE/DEPT_DELETE',
    `target_type`     VARCHAR(16)  NOT NULL COMMENT 'USER / DEPT',
    `target_id`       BIGINT       NOT NULL COMMENT '本地用户/部门ID',
    `payload`         TEXT         COMMENT '推送内容 JSON',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1成功 2失败(超过重试次数)',
    `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_time` DATETIME     DEFAULT NULL COMMENT '下一次重试时间',
    `last_error`      VARCHAR(1000) DEFAULT NULL COMMENT '最近一次错误',
    `create_time`     DATETIME     DEFAULT NULL,
    `update_time`     DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_status_next` (`status`, `next_retry_time`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='外部通讯录出站推送 Outbox';
