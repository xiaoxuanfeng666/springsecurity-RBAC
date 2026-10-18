-- =============================================================================
-- 增量升级：外部通讯录 / 部门同步
-- 日期：2026-10-18
-- 目标库：rbac
-- =============================================================================
-- 外部部门（企业微信 department id 等）与本地 sys_dept 的映射。
-- =============================================================================

USE `rbac`;

CREATE TABLE IF NOT EXISTS `sys_dept_identity` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `provider`         VARCHAR(32)  NOT NULL COMMENT '身份/通讯录提供方：wecom / mock',
    `corp_id`          VARCHAR(64)  NOT NULL COMMENT '企业ID',
    `external_dept_id` VARCHAR(128) NOT NULL COMMENT '外部部门ID',
    `dept_id`          BIGINT       NOT NULL COMMENT '本地部门ID（sys_dept.id）',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0失效',
    `create_time`      DATETIME     DEFAULT NULL,
    `update_time`      DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_identity` (`provider`, `corp_id`, `external_dept_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='外部部门与本地组织节点映射';
