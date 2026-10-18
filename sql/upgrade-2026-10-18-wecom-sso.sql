-- =============================================================================
-- 增量升级：企业微信扫码登录（MVP）
-- 日期：2026-10-18
-- 目标库：rbac（rbac-oauth 与 rbac-backend 共用）
-- =============================================================================
-- ① 用户与外部身份（企业微信 userid）的绑定关系；
-- ② SSO 会话记录增加登录方式，便于审计（PASSWORD / WECOM）。
--
-- 执行：
--   mysql -uroot -p --default-character-set=utf8mb4 < sql/upgrade-2026-10-18-wecom-sso.sql
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_user_identity` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT       NOT NULL COMMENT '本地用户ID（sys_user.id）',
    `provider`         VARCHAR(32)  NOT NULL COMMENT '身份提供方：wecom / dingtalk / oidc',
    `corp_id`          VARCHAR(64)  NOT NULL COMMENT '企业ID（企业微信 CorpID）',
    `external_user_id` VARCHAR(128) NOT NULL COMMENT '外部用户ID（企业微信 userid）',
    `union_id`         VARCHAR(128) DEFAULT NULL COMMENT '企业微信 unionid（可选）',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0解绑',
    `bind_time`        DATETIME     DEFAULT NULL COMMENT '绑定时间',
    `last_login_time`  DATETIME     DEFAULT NULL COMMENT '最近一次通过该身份登录的时间',
    `remark`           VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time`      DATETIME     DEFAULT NULL,
    `update_time`      DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity` (`provider`, `corp_id`, `external_user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户外部身份绑定';

ALTER TABLE `sys_sso_session`
    ADD COLUMN `login_type` VARCHAR(32) NOT NULL DEFAULT 'PASSWORD'
        COMMENT '登录方式：PASSWORD / WECOM' AFTER `device`;
