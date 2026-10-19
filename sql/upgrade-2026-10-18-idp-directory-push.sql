-- =============================================================================
-- 增量升级：外部通讯录推送事件（Push + Pull 混合同步）
-- 日期：2026-10-18
-- 目标库：rbac
-- =============================================================================
-- 外部系统推送的用户/部门变更事件。provider + event_id 唯一，用于幂等去重。
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_directory_event` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `provider`      VARCHAR(32)  NOT NULL COMMENT '来源：wecom / dingtalk / mock ...',
    `event_id`      VARCHAR(128) NOT NULL COMMENT '外部事件ID（幂等键）',
    `event_type`    VARCHAR(32)  NOT NULL COMMENT 'USER_CREATED/USER_UPDATED/USER_DELETED/DEPT_CREATED/DEPT_UPDATED/DEPT_DELETED',
    `payload`       TEXT         COMMENT '原始事件 JSON',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1已接收 2已处理 3失败',
    `error_msg`     VARCHAR(1000) DEFAULT NULL,
    `received_time` DATETIME     DEFAULT NULL,
    `processed_time` DATETIME    DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_event` (`provider`, `event_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='外部通讯录推送事件（幂等）';
