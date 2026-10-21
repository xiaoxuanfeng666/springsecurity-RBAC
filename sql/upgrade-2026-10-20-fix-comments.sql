-- =============================================================================
-- 修复：sys_user / sys_role / sys_menu / sys_role_menu / sys_user_role 的注释乱码
-- 日期：2026-10-20
-- 目标库：rbac
-- =============================================================================
-- 现象：
--   部分列注释与表注释是「UTF-8 文本被按 GBK 解码后再以 UTF-8 存储」的双重编码
--   （如 `鐢ㄦ埛ID` 实际是 `用户ID`），另一些列是正常的 —— 说明是某次通过 GBK
--   连接的导入/ALTER 造成的，不是客户端显示问题。
--
-- 处理：
--   仅重写注释，不改类型、可空性、默认值与索引；列定义逐字取自当前 SHOW CREATE TABLE。
--   sys_user.status 的注释按当前代码语义（1在职 0停用 2离职）补齐，原文本因
--   字符丢失已无法完整还原（`狀態：1啟用 0禁用`）。
--
-- 【执行】必须带 utf8mb4，否则中文会再次写坏：
--   mysql -uroot -p --default-character-set=utf8mb4 rbac < upgrade-2026-10-20-fix-comments.sql
-- =============================================================================

USE `rbac`;

ALTER TABLE `sys_user`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    MODIFY COLUMN `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
    MODIFY COLUMN `password` VARCHAR(128) NOT NULL COMMENT '密码（BCrypt）',
    MODIFY COLUMN `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    MODIFY COLUMN `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    MODIFY COLUMN `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    MODIFY COLUMN `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1在职 0停用 2离职',
    MODIFY COLUMN `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
    MODIFY COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    MODIFY COLUMN `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    MODIFY COLUMN `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    COMMENT = '用户表';

ALTER TABLE `sys_role`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    MODIFY COLUMN `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    MODIFY COLUMN `role_key` VARCHAR(64) NOT NULL COMMENT '角色标识，如 admin',
    MODIFY COLUMN `sort` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    MODIFY COLUMN `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    MODIFY COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    COMMENT = '角色表';

ALTER TABLE `sys_menu`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    MODIFY COLUMN `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    MODIFY COLUMN `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
    MODIFY COLUMN `path` VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
    MODIFY COLUMN `component` VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    MODIFY COLUMN `perms` VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 system:user:list',
    MODIFY COLUMN `type` TINYINT NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
    MODIFY COLUMN `icon` VARCHAR(64) DEFAULT NULL COMMENT '图标',
    MODIFY COLUMN `sort` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
    MODIFY COLUMN `visible` TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示：1显示 0隐藏',
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    COMMENT = '菜单权限表';

ALTER TABLE `sys_role_menu`
    MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色ID',
    MODIFY COLUMN `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    COMMENT = '角色菜单关联表';

ALTER TABLE `sys_user_role`
    MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '用户ID',
    MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色ID',
    COMMENT = '用户角色关联表';
