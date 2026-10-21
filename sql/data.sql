-- ============================================================
-- RBAC 权限管理系统 初始化数据
-- 默认密码统一为：admin123
--   admin    / admin123  超级管理员
--   zhangsan / admin123  普通用户
-- ============================================================

USE `rbac`;

-- ---------- 组织架构（公司 / 分公司 / 部门）----------
-- type：1公司 2分公司 3部门
INSERT INTO `sys_dept` (`id`, `parent_id`, `dept_name`, `type`, `sort`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_time`, `update_time`)
VALUES (1, 0, '总公司', 1, 1, '张三丰', '13800000000', 'hq@example.com', 1, 0, NOW(), NOW()),
       (2, 1, '北京分公司', 2, 1, '李四', '13800000010', 'bj@example.com', 1, 0, NOW(), NOW()),
       (3, 1, '上海分公司', 2, 2, '王五', '13800000020', 'sh@example.com', 1, 0, NOW(), NOW()),
       (4, 2, '研发部', 3, 1, '李四', '13800000010', 'rd@example.com', 1, 0, NOW(), NOW()),
       (5, 4, '前端组', 3, 1, '赵六', '13800000030', 'fe@example.com', 1, 0, NOW(), NOW()),
       (6, 4, '后端组', 3, 2, '钱七', '13800000040', 'be@example.com', 1, 0, NOW(), NOW()),
       (7, 3, '市场部', 3, 1, '王五', '13800000020', 'mkt@example.com', 1, 0, NOW(), NOW()),
       (8, 3, '销售部', 3, 2, '孙八', '13800000050', 'sales@example.com', 1, 0, NOW(), NOW());

-- ---------- 岗位 ----------
INSERT INTO `sys_post` (`id`, `post_code`, `post_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'ceo', '董事长', 1, 1, 0, '全局管理', NOW(), NOW()),
       (2, 'pm', '项目经理', 2, 1, 0, '项目负责人', NOW(), NOW()),
       (3, 'hr', '人力资源', 3, 1, 0, '人事相关', NOW(), NOW()),
       (4, 'staff', '普通员工', 4, 1, 0, '默认岗位', NOW(), NOW());

-- ---------- 用户 ----------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `dept_id`, `email`, `phone`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'admin', '$2b$10$difBcjnEBpNcRUTaL/9iduXuOjB5ohvM11FwkbtE5C.4qHuxzazou', '超级管理员', 1, 'admin@example.com', '13800000001', 1, 0, '系统内置管理员', NOW(), NOW()),
       (2, 'zhangsan', '$2b$10$difBcjnEBpNcRUTaL/9iduXuOjB5ohvM11FwkbtE5C.4qHuxzazou', '张三', 5, 'zhangsan@example.com', '13800000002', 1, 0, '普通用户示例', NOW(), NOW());

-- ---------- 用户-岗位 ----------
INSERT INTO `sys_user_post` (`id`, `user_id`, `post_id`)
VALUES (1, 1, 1),
       (2, 2, 4);

-- ---------- 角色 ----------
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, '超级管理员', 'admin', 1, 1, 0, '拥有全部权限', NOW(), NOW()),
       (2, '普通用户', 'common', 2, 1, 0, '仅可查看部分数据', NOW(), NOW());

-- ---------- 用户-角色 ----------
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`)
VALUES (1, 1, 1),
       (2, 2, 2);

-- ---------- 菜单/权限 ----------
-- 顶级目录与顶级菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1, 0, '系统管理', '/system', 'Layout', NULL, 1, 'Setting', 2, 1, 1, NOW(), NOW()),
       (2, 0, '仪表盘', '/dashboard', 'dashboard/index', NULL, 2, 'Odometer', 1, 1, 1, NOW(), NOW());

-- 二级菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (100, 1, '用户管理', 'user', 'system/user/index', 'system:user:list', 2, 'User', 1, 1, 1, NOW(), NOW()),
       (101, 1, '角色管理', 'role', 'system/role/index', 'system:role:list', 2, 'UserFilled', 2, 1, 1, NOW(), NOW()),
       (102, 1, '菜单管理', 'menu', 'system/menu/index', 'system:menu:list', 2, 'Menu', 3, 1, 1, NOW(), NOW()),
       (103, 1, '部门管理', 'dept', 'system/dept/index', 'system:dept:list', 2, 'OfficeBuilding', 4, 1, 1, NOW(), NOW()),
       (105, 1, '岗位管理', 'post', 'system/post/index', 'system:post:list', 2, 'Postcard', 5, 1, 1, NOW(), NOW()),
       (106, 1, '操作日志', 'audit', 'system/audit/index', 'system:audit:list', 2, 'Document', 6, 1, 1, NOW(), NOW()),
       (107, 1, '客户端管理', 'client', 'system/client/index', 'system:client:list', 2, 'Key', 7, 1, 1, NOW(), NOW());

-- 按钮权限：用户管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1001, 100, '用户查询', NULL, NULL, 'system:user:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1002, 100, '用户新增', NULL, NULL, 'system:user:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1003, 100, '用户修改', NULL, NULL, 'system:user:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1004, 100, '用户删除', NULL, NULL, 'system:user:remove', 3, NULL, 4, 1, 1, NOW(), NOW()),
       (1005, 100, '重置密码', NULL, NULL, 'system:user:resetPwd', 3, NULL, 5, 1, 1, NOW(), NOW()),
       (1006, 100, '分配角色', NULL, NULL, 'system:user:role', 3, NULL, 6, 1, 1, NOW(), NOW()),
       (1007, 100, '用户停用/启用', NULL, NULL, 'system:user:status', 3, NULL, 7, 1, 1, NOW(), NOW()),
       (1008, 100, '用户离职/复职', NULL, NULL, 'system:user:resign', 3, NULL, 8, 1, 1, NOW(), NOW()),
       (1009, 100, '身份绑定', NULL, NULL, 'system:user:identity', 3, NULL, 9, 1, 1, NOW(), NOW()),
       (1010, 100, '推送外部用户', NULL, NULL, 'system:user:sync', 3, NULL, 10, 1, 1, NOW(), NOW());

-- 按钮权限：角色管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1011, 101, '角色查询', NULL, NULL, 'system:role:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1012, 101, '角色新增', NULL, NULL, 'system:role:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1013, 101, '角色修改', NULL, NULL, 'system:role:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1014, 101, '角色删除', NULL, NULL, 'system:role:remove', 3, NULL, 4, 1, 1, NOW(), NOW()),
       (1015, 101, '分配权限', NULL, NULL, 'system:role:menu', 3, NULL, 5, 1, 1, NOW(), NOW());

-- 按钮权限：菜单管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1021, 102, '菜单查询', NULL, NULL, 'system:menu:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1022, 102, '菜单新增', NULL, NULL, 'system:menu:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1023, 102, '菜单修改', NULL, NULL, 'system:menu:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1024, 102, '菜单删除', NULL, NULL, 'system:menu:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- 按钮权限：部门管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1031, 103, '部门查询', NULL, NULL, 'system:dept:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1032, 103, '部门新增', NULL, NULL, 'system:dept:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1033, 103, '部门修改', NULL, NULL, 'system:dept:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1034, 103, '部门删除', NULL, NULL, 'system:dept:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- 按钮权限：岗位管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1051, 105, '岗位查询', NULL, NULL, 'system:post:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1052, 105, '岗位新增', NULL, NULL, 'system:post:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1053, 105, '岗位修改', NULL, NULL, 'system:post:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1054, 105, '岗位删除', NULL, NULL, 'system:post:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- 按钮权限：客户端管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1071, 107, '客户端查询', NULL, NULL, 'system:client:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1072, 107, '客户端新增', NULL, NULL, 'system:client:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1073, 107, '客户端修改', NULL, NULL, 'system:client:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1074, 107, '客户端删除', NULL, NULL, 'system:client:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- 二级菜单：SSO 会话
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (110, 1, 'SSO 会话', 'sso-session', 'system/sso-session/index', 'system:sso:list', 2, 'Connection', 10, 1, 1, NOW(), NOW());

-- 按钮权限：SSO 会话
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1095, 110, '会话查询', NULL, NULL, 'system:sso:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1096, 110, '强制下线', NULL, NULL, 'system:sso:kick', 3, NULL, 2, 1, 1, NOW(), NOW());

-- ---------- 子系统菜单权限（RBAC 给子系统配菜单）----------
-- 这里挂的是「管理子系统菜单」这个后台功能本身，与 sys_subsystem_menu（被管理的子系统菜单）是两回事
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (108, 1, '子系统管理', 'subsystem', 'system/subsystem/index', 'system:subsystem:list', 2, 'Grid', 8, 1, 1, NOW(), NOW()),
       (109, 1, '子系统菜单管理', 'subsystem-menu', 'system/subsystem-menu/index', 'system:subsystem:menu:list', 2, 'Menu', 9, 1, 1, NOW(), NOW());

-- 按钮权限：子系统管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1081, 108, '子系统查询', NULL, NULL, 'system:subsystem:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1082, 108, '子系统新增', NULL, NULL, 'system:subsystem:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1083, 108, '子系统修改', NULL, NULL, 'system:subsystem:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1084, 108, '子系统删除', NULL, NULL, 'system:subsystem:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- 按钮权限：子系统菜单管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1091, 109, '子系统菜单查询', NULL, NULL, 'system:subsystem:menu:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1092, 109, '子系统菜单新增', NULL, NULL, 'system:subsystem:menu:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1093, 109, '子系统菜单修改', NULL, NULL, 'system:subsystem:menu:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1094, 109, '子系统菜单删除', NULL, NULL, 'system:subsystem:menu:remove', 3, NULL, 4, 1, 1, NOW(), NOW());

-- ---------- 角色-菜单：admin 刻意不插任何记录 ----------
-- 超级管理员的权限由代码隐式授予（见 rbac-backend 的 security/SuperAdmin）：
-- 权限判定用通配符 *:*:* 一律放行，菜单树直接返回全部启用菜单。
-- 因此 role_id=1 在 sys_role_menu 里**没有行**，新增菜单后也无需给超级管理员补授权。
-- 换句话说，admin 角色在这张表里的数据不代表它的真实权限（那张表只管普通角色）。

-- ---------- 角色-菜单：common 仅有仪表盘 + 用户查询 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (2, 2), (2, 1), (2, 100), (2, 1001);

-- ---------- 示例 OAuth2 客户端（第三方系统接入，覆盖全部五种授权模式）----------
-- appSecret 存的是明文 client123 经 AES 加密后的密文（密钥来自配置项 oauth2.client-secret-key）。
-- 之所以用可逆加密而非哈希，是因为「客户端管理」页面需要把明文展示出来供复制给第三方。
-- 若更换了 oauth2.client-secret-key，这条密文将无法解密，需在页面上重新设置密钥。
-- 授权码与隐式模式由浏览器发起（response_type=code / token），登录页与确认页由
-- rbac-oauth-web（授权服务器的独立前端工程）提供；
-- 隐式模式要求回调地址已登记在 redirectUrl 中，令牌以 URL fragment 形式返回。
-- 【本项目定制】授权地址必须带上 client_secret，服务端会校验（标准 OAuth2 不需要这一步，
-- 密钥原本只在令牌端点使用）。浏览器直接打开下面这个地址即可走通授权码流程：
--   http://localhost:7071/oauth/authorize?response_type=code&client_id=rbac-third-party&client_secret=client123&redirect_uri=http://localhost:7071/oauth/test-callback&scope=read&state=abc
-- 注意：密钥会因此出现在浏览器历史与访问日志中，生产环境需在网关层做日志脱敏。
-- redirectUrl 最后一项是授权服务自带的授权码回调联调端点（GET /oauth/test-callback），
-- 换用其他回调地址时必须一并登记，否则授权端点会以「回调地址不匹配」拒绝。
-- autoApproveScopes 留空：客户端若把请求的 scope 全部列为自动放行，用户不会看到授权确认页
-- （默认确认页已被替换为自定义页面 rbac-oauth-web 的 /approve，见 OAuth2ApprovalConfig）。
-- 联调示例（OAuth2 端点都在授权服务 rbac-oauth 上，端口 7071）：
--   curl -u rbac-third-party:client123 -d grant_type=client_credentials http://localhost:7071/oauth/token
--   curl -u rbac-third-party:client123 -d grant_type=password -d username=admin -d password=admin123 http://localhost:7071/oauth/token
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additionalInformation`, `autoApproveScopes`)
VALUES ('rbac-third-party', NULL, 'cnZXzMbXqybU6Pwk0x4XTbHWZOgE8o9GCrb4JKkrLIHxu84jyQ==',
        'read,write', 'authorization_code,password,implicit,client_credentials,refresh_token',
        'http://localhost:8080/callback,http://localhost:5173/oauth/callback,http://localhost:7071/oauth/test-callback',
        'ROLE_CLIENT', 1800, 604800, NULL, NULL);

-- ---------- 营销系统（真实接入的独立子系统）----------
-- 营销系统有自己的独立库（marketing）、独立前后端，没有自己的权限管理：
-- 登录授权走本授权服务，权限数据由 RBAC 经 RocketMQ 下发。
-- redirectUrl 里的 5175 是营销系统前端的回调页（授权码模式），换令牌时校验必须完全一致。
-- autoApproveScopes='read'：放行子系统申请的 read 范围，授权时不再弹确认页 ——
-- 统一门户的「一键登录所有子系统」在隐藏 iframe 里跑授权码流程，弹出确认页就会卡死。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additionalInformation`, `autoApproveScopes`)
VALUES ('marketing', NULL, 'a/oSsMCdauFQuChaDN2gkKH5lebhgGHFUJ2GQWzgmFaDLBpq/jYbfg==',
        'read,write', 'authorization_code,password,refresh_token',
        'http://localhost:5175/oauth/callback',
        'ROLE_CLIENT', 1800, 604800, NULL, 'read');

-- ---------- 订单中心（真实接入的独立子系统）----------
-- 与营销系统同样的模式：独立库（order_center）、独立前后端，没有自己的权限管理。
-- redirectUrl 里的 5176 是订单中心前端的回调页（授权码模式）。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additionalInformation`, `autoApproveScopes`)
VALUES ('order-center', NULL, 'yIBwx+lZmE1+AWObckeSg200SjEGeNXqms+f7p8BB3ZigJhE',
        'read,write', 'authorization_code,password,refresh_token',
        'http://localhost:5176/oauth/callback',
        'ROLE_CLIENT', 1800, 604800, NULL, 'read');

-- ---------- 统一门户（企业级 SSO 的入口，5177）----------
-- 与子系统客户端的关键差别：
--   * 不给 password 模式 —— 门户只走授权码，用户必须在授权服务的登录页认证，
--     这样浏览器里才会有 SESSION（一键登录其余子系统正是靠这个 SESSION 免去二次登录）；
--   * 不给 client_credentials —— 门户代表的是人，不是机器身份。
-- appSecret 是 portal123 的 AES 密文。autoApproveScopes='read'：门户是入口页，
-- 让用户在第一次进门户时先点一次「同意授权」纯属多余，这里直接放行。
INSERT INTO `clientdetails` (`appId`, `resourceIds`, `appSecret`, `scope`, `grantTypes`, `redirectUrl`, `authorities`, `access_token_validity`, `refresh_token_validity`, `additionalInformation`, `autoApproveScopes`)
VALUES ('portal', NULL, 'Rxo21XV1MhryGGFp6DCuXtwIU2fEF3RVmNimrWfS53BrYqubCg==',
        'read', 'authorization_code,refresh_token',
        'http://localhost:5177/oauth/callback',
        'ROLE_CLIENT', 1800, 604800, NULL, 'read');

-- ---------- 子系统及其菜单 ----------
-- sys_subsystem_menu 里的菜单只用于「下发给子系统」，与 RBAC 后台自己的 sys_menu 无关：
-- type 为 1目录 / 2菜单 / 3按钮，按钮才是增删改查的权限点（带 perms）。
INSERT INTO `sys_subsystem` (`id`, `subsystem_code`, `subsystem_name`, `sort`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'order-center', '订单中心', 1, 1, 0, '订单的增删改查与统计汇总', NOW(), NOW()),
       (2, 'marketing', '营销系统', 2, 1, 0, '抽奖活动、积分任务与流水记录', NOW(), NOW());

-- 订单中心的菜单树：与订单中心前端 order-frontend/src/views 下的路径一一对应。
-- 主键从 201 起，与营销系统的 100~121 分段错开，避免两边撞号。
INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1, 1, 0, '订单管理', 1, NULL, '/order', 'Layout', 'Document', 1, 1, 1, NOW(), NOW()),
       (2, 1, 1, '订单列表', 2, 'order:order:list', 'list', 'order/list/index', 'List', 1, 1, 1, NOW(), NOW()),
       (3, 1, 1, '订单统计', 2, 'order:stat:list', 'stat', 'order/stat/index', 'DataLine', 2, 1, 1, NOW(), NOW()),
       (201, 1, 2, '订单查询', 3, 'order:order:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
       (202, 1, 2, '订单新增', 3, 'order:order:add', NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
       (203, 1, 2, '订单修改', 3, 'order:order:edit', NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
       (204, 1, 2, '订单删除', 3, 'order:order:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW());

-- 营销系统的菜单树：菜单节点自带 list 权限，按钮节点是细化权限点，
-- component 与营销前端 marketing-frontend/src/views 下的路径一一对应。
INSERT INTO `sys_subsystem_menu` (`id`, `subsystem_id`, `parent_id`, `menu_name`, `type`, `perms`, `path`, `component`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES
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
    (110, 2, 0, '积分管理', 1, NULL, '/points', 'Layout', 'Coin', 2, 1, 1, NOW(), NOW()),
    (111, 2, 110, '积分任务', 2, 'marketing:task:list', 'task', 'points/task/index', 'List', 1, 1, 1, NOW(), NOW()),
    (112, 2, 111, '任务查询', 3, 'marketing:task:query', NULL, NULL, NULL, 1, 0, 1, NOW(), NOW()),
    (113, 2, 111, '任务新增', 3, 'marketing:task:add', NULL, NULL, NULL, 2, 0, 1, NOW(), NOW()),
    (114, 2, 111, '任务修改', 3, 'marketing:task:edit', NULL, NULL, NULL, 3, 0, 1, NOW(), NOW()),
    (115, 2, 111, '任务删除', 3, 'marketing:task:remove', NULL, NULL, NULL, 4, 0, 1, NOW(), NOW()),
    (116, 2, 110, '积分流水', 2, 'marketing:points:list', 'flow', 'points/flow/index', 'DataLine', 2, 1, 1, NOW(), NOW());

-- 普通角色(common)在每个子系统里都只拿到只读页面（订单列表/订单统计、抽奖记录/积分流水），
-- 用于演示「权限经 MQ 下发 → 子系统只能看到被授权的菜单、写接口 403」。
-- 超级管理员(admin)在这里没有任何记录：它的权限由代码隐式给全集。
INSERT INTO `sys_role_subsystem_menu` (`role_id`, `subsystem_menu_id`)
VALUES (2, 1), (2, 2), (2, 3),                                      -- 订单中心：目录 + 两个只读页面
       (2, 100), (2, 106), (2, 110), (2, 116);                      -- 营销系统：目录 + 两个只读页面
