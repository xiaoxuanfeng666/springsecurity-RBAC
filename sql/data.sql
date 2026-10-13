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
       (105, 1, '岗位管理', 'post', 'system/post/index', 'system:post:list', 2, 'Postcard', 5, 1, 1, NOW(), NOW());

-- 按钮权限：用户管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `type`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`)
VALUES (1001, 100, '用户查询', NULL, NULL, 'system:user:query', 3, NULL, 1, 1, 1, NOW(), NOW()),
       (1002, 100, '用户新增', NULL, NULL, 'system:user:add', 3, NULL, 2, 1, 1, NOW(), NOW()),
       (1003, 100, '用户修改', NULL, NULL, 'system:user:edit', 3, NULL, 3, 1, 1, NOW(), NOW()),
       (1004, 100, '用户删除', NULL, NULL, 'system:user:remove', 3, NULL, 4, 1, 1, NOW(), NOW()),
       (1005, 100, '重置密码', NULL, NULL, 'system:user:resetPwd', 3, NULL, 5, 1, 1, NOW(), NOW()),
       (1006, 100, '分配角色', NULL, NULL, 'system:user:role', 3, NULL, 6, 1, 1, NOW(), NOW());

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

-- ---------- 角色-菜单：admin 拥有全部 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu`;

-- ---------- 角色-菜单：common 仅有仪表盘 + 用户查询 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (2, 2), (2, 1), (2, 100), (2, 1001);
