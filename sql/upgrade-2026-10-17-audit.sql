-- =============================================================================
-- 增量升级：审计日志系统接入（RBAC 侧）
-- 日期：2026-10-17
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 本脚本做三件事：
--   ① 新增 OAuth2 客户端 audit（审计日志系统前端用它走授权码模式换令牌）
--   ② 登记子系统「审计日志系统」并铺一套菜单 + 按钮权限点
--   ③ 给普通角色(common)授只读页面
--
-- 审计日志系统自己的业务库是**完全独立的** `audit_log` 库，
-- 由 audit-schema.sql 单独创建，与 RBAC 库没有任何表共用、没有外键关联。
--
-- 依赖：本脚本假设 upgrade-2026-10-16-marketing.sql 已执行
--       （sys_subsystem_menu 已有 perms 列、type 允许 3=按钮）。
-- =============================================================================

USE `rbac`;

-- ---------- ① 审计日志系统的 OAuth2 客户端 ----------
-- appSecret 是 'audit123' 的 AES/GCM 密文（密钥同 oauth2.client-secret-key）。
-- redirectUrl 里的 5178 是审计前端 audit-frontend 的回调页，必须完全一致。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`,
                             `authorities`, `access_token_validity`, `refresh_token_validity`,
                             `additionalInformation`, `autoApproveScopes`)
VALUES ('audit', NULL, 'kZ2anm0yEQ68PR/L0GYu48V0NQ1kV45zFr4SRNHKleYcATvL',
        'read', 'authorization_code,refresh_token',
        'http://localhost:5178/oauth/callback',
        'ROLE_CLIENT', 3600, 604800, NULL, 'read');

-- ---------- ② 子系统登记 + 菜单/按钮种子 ----------
INSERT INTO `sys_subsystem` (`id`, `subsystem_code`, `subsystem_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (3, 'audit', '审计日志系统', 3, 1, 0, '跨系统增删改接口操作审计', NOW(), NOW());

-- 菜单树约定（与审计前端 audit-frontend 的 views 目录一一对应）：
--   目录 /audit 的 component 是 Layout，菜单的 path 是相对子路径，
--   component 是审计前端里的组件路径。
INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES
    (300, 3, 0, '审计日志', 1, NULL, '/audit', 'Layout', 'Document', 1, 1, 1, NOW(), NOW()),
    (301, 3, 300, '操作日志', 2, 'audit:log:list', 'log', 'audit/index', 'List', 1, 1, 1, NOW(), NOW()),
    (302, 3, 301, '日志查询', 3, 'audit:log:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW());

-- ---------- ③ 普通角色(common)的审计系统授权 ----------
-- 只授目录 + 只读页面 + 查询按钮：zhangsan 能查审计日志，但没有写入口。
-- 超级管理员(admin)不在这里授权：它的权限由代码隐式给全集（见 SuperAdmin 类）。
INSERT INTO `sys_role_subsystem_menu` (`role_id`, `subsystem_menu_id`)
VALUES (2, 300),
       (2, 301),
       (2, 302);

-- 校验：应看到 3 条菜单（1 目录 + 1 菜单 + 1 按钮）、3 条角色授权
-- SELECT type, COUNT(*) FROM sys_subsystem_menu WHERE subsystem_id = 3 GROUP BY type;
-- SELECT COUNT(*) FROM sys_role_subsystem_menu rsm
--   JOIN sys_subsystem_menu sm ON rsm.subsystem_menu_id = sm.id WHERE sm.subsystem_id = 3;
