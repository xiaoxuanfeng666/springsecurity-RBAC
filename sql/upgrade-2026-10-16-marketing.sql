-- =============================================================================
-- 增量升级：营销系统接入（RBAC 侧）
-- 日期：2026-10-16
-- 目标库：rbac        （本脚本只动 RBAC 库）
-- =============================================================================
-- 本脚本做四件事：
--   ① sys_subsystem_menu 补 perms 列并放开按钮类型（1目录 2菜单 3按钮）
--   ② 新增 OAuth2 客户端 marketing（营销系统用它走密码模式/授权码模式换令牌）
--   ③ 登记子系统「营销系统」并铺一套菜单 + 按钮权限点
--   ④ 给普通角色(common)授两个只读页面，用于演示 MQ 下发与权限拦截
--
-- 营销系统自己的业务库是**完全独立的** `marketing` 库，
-- 由 marketing-schema.sql 单独创建，与 RBAC 库没有任何表共用、没有外键关联。
-- RBAC 库这边只多出「这个子系统叫什么、有哪些菜单、哪个角色能用」的配置数据。
--
-- 【注意】本脚本含 ALTER TABLE，只能执行一次。
-- =============================================================================

USE `rbac`;

-- ---------- ① 子系统菜单补权限标识与按钮类型 ----------
ALTER TABLE `sys_subsystem_menu`
    ADD COLUMN `perms` VARCHAR(100) DEFAULT NULL COMMENT '权限标识，如 marketing:activity:add' AFTER `type`;

ALTER TABLE `sys_subsystem_menu`
    MODIFY COLUMN `type` TINYINT NOT NULL DEFAULT 2 COMMENT '类型：1目录 2菜单 3按钮';

-- ---------- ② 营销系统的 OAuth2 客户端 ----------
-- appSecret 是 'marketing123' 的 AES/GCM 密文（密钥同 oauth2.client-secret-key）。
-- 库中只存密文，rbac-oauth 认证时解密比对；要改密钥请在 RBAC 的「客户端管理」页面重设。
-- redirectUrl 里的 5175 是营销系统前端的回调页，授权码模式换令牌时校验必须完全一致。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`,
                             `authorities`, `access_token_validity`, `refresh_token_validity`,
                             `additionalInformation`, `autoApproveScopes`)
VALUES ('marketing', NULL, 'a/oSsMCdauFQuChaDN2gkKH5lebhgGHFUJ2GQWzgmFaDLBpq/jYbfg==',
        'read,write', 'authorization_code,password,refresh_token',
        'http://localhost:5175/oauth/callback',
        'ROLE_CLIENT', 3600, 604800, NULL, NULL);

-- ---------- ③ 子系统登记 + 菜单/按钮种子 ----------
INSERT INTO `sys_subsystem` (`id`, `subsystem_code`, `subsystem_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (2, 'marketing', '营销系统', 2, 1, 0, '抽奖活动、积分任务与流水记录', NOW(), NOW());

-- 菜单树约定（与营销前端 marketing-frontend 的 views 目录一一对应）：
--   目录 /lottery、/points 的 component 是 Layout，菜单的 path 是相对子路径，
--   component 是营销前端里的组件路径。
--   菜单节点（type=2）自带 list 权限，按钮节点（type=3）是增删改查的细化权限点。
INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES
    -- 抽奖管理
    (100, 2, 0, '抽奖管理', 1, NULL, '/lottery', 'Layout', 'Trophy', 1, 1, 1, NOW(), NOW()),
    (101, 2, 100, '抽奖活动', 2, 'marketing:activity:list', 'activity', 'lottery/activity/index', 'Tickets', 1, 1, 1, NOW(), NOW()),
    (102, 2, 101, '活动查询', 3, 'marketing:activity:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
    (103, 2, 101, '活动新增', 3, 'marketing:activity:add', NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
    (104, 2, 101, '活动修改', 3, 'marketing:activity:edit', NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
    (105, 2, 101, '活动删除', 3, 'marketing:activity:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW()),
    (106, 2, 100, '抽奖记录', 2, 'marketing:record:list', 'record', 'lottery/record/index', 'Memo', 2, 1, 1, NOW(), NOW()),
    (117, 2, 100, '奖品配置', 2, 'marketing:prize:list', 'prize', 'lottery/prize/index', 'Present', 3, 1, 1, NOW(), NOW()),
    (118, 2, 117, '奖品查询', 3, 'marketing:prize:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
    (119, 2, 117, '奖品新增', 3, 'marketing:prize:add', NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
    (120, 2, 117, '奖品修改', 3, 'marketing:prize:edit', NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
    (121, 2, 117, '奖品删除', 3, 'marketing:prize:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW()),
    -- 积分管理
    (110, 2, 0, '积分管理', 1, NULL, '/points', 'Layout', 'Coin', 2, 1, 1, NOW(), NOW()),
    (111, 2, 110, '积分任务', 2, 'marketing:task:list', 'task', 'points/task/index', 'List', 1, 1, 1, NOW(), NOW()),
    (112, 2, 111, '任务查询', 3, 'marketing:task:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
    (113, 2, 111, '任务新增', 3, 'marketing:task:add', NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
    (114, 2, 111, '任务修改', 3, 'marketing:task:edit', NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
    (115, 2, 111, '任务删除', 3, 'marketing:task:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW()),
    (116, 2, 110, '积分流水', 2, 'marketing:points:list', 'flow', 'points/flow/index', 'DataLine', 2, 1, 1, NOW(), NOW());

-- ---------- ④ 普通角色(common)的营销系统授权 ----------
-- 只授两个只读页面（抽奖记录、积分流水），不带任何按钮：
-- 用 zhangsan 登录营销系统能看到这两个菜单，但没有新增/修改/删除入口，
-- 直接调写接口会 403 —— 这就是「权限经 MQ 下发后由子系统自己拦截」的演示。
-- 超级管理员(admin)不在这里授权：它的权限由代码隐式给全集（见 SuperAdmin 类）。
INSERT INTO `sys_role_subsystem_menu` (`role_id`, `subsystem_menu_id`)
VALUES (2, 100),  -- 抽奖管理（目录，导航用）
       (2, 106),  -- 抽奖记录
       (2, 110),  -- 积分管理（目录，导航用）
       (2, 116);  -- 积分流水

-- 校验：应看到 19 条菜单（2 目录 + 5 菜单 + 12 按钮）、4 条角色授权
-- SELECT type, COUNT(*) FROM sys_subsystem_menu WHERE subsystem_id = 2 GROUP BY type;
-- SELECT COUNT(*) FROM sys_role_subsystem_menu rsm
--   JOIN sys_subsystem_menu sm ON rsm.subsystem_menu_id = sm.id WHERE sm.subsystem_id = 2;
