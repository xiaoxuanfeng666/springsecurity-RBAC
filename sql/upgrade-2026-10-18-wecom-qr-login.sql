-- =============================================================================
-- 增量升级：企业微信扫码登录短链票据
-- 日期：2026-10-18
-- 目标库：rbac（rbac-oauth 使用）
-- =============================================================================
-- 二维码内容是一个带有效期的短链 /q/{code}。
-- 票据同时写入本表与 Redis（oauth2:qr:login:{code}），Redis 负责 TTL 快速过期，
-- 数据库负责持久化与审计。
--
-- 执行：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-qr-login.sql
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_qr_login_ticket` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `code`           VARCHAR(32)  NOT NULL COMMENT '短链码',
    `provider`       VARCHAR(32)  NOT NULL DEFAULT 'wecom' COMMENT '身份提供方',
    `corp_id`        VARCHAR(64)  DEFAULT NULL COMMENT '企业ID',
    `external_user_id` VARCHAR(128) DEFAULT NULL COMMENT '扫码确认后的外部用户ID',
    `username`       VARCHAR(64)  DEFAULT NULL COMMENT '扫码确认后的本地用户名',
    `return_to`      VARCHAR(512) DEFAULT NULL COMMENT '登录完成后回跳地址',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/USED/EXPIRED/FAILED',
    `login_code`     VARCHAR(64)  DEFAULT NULL COMMENT '电脑端完成登录的一次性校验码',
    `error_msg`      VARCHAR(255) DEFAULT NULL,
    `expire_time`    DATETIME     NOT NULL COMMENT '过期时间',
    `confirmed_time` DATETIME     DEFAULT NULL,
    `used_time`      DATETIME     DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT NULL,
    `update_time`    DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qr_code` (`code`),
    KEY `idx_qr_expire` (`expire_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='企业微信扫码登录短链票据';
