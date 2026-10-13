-- ============================================================
-- RBAC 权限管理系统 初始化数据
-- 默认密码统一为：admin123
--   admin    / admin123  超级管理员
--   zhangsan / admin123  普通用户
-- ============================================================

USE `rbac`;

-- ---------- 用户 ----------
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `status`, `del_flag`, `remark`, `create_time`, `update_time`)
VALUES (1, 'admin', '$2b$10$difBcjnEBpNcRUTaL/9iduXuOjB5ohvM11FwkbtE5C.4qHuxzazou', '超级管理员', 'admin@example.com', '13800000001', 1, 0, '系统内置管理员', NOW(), NOW()),
       (2, 'zhangsan', '$2b$10$difBcjnEBpNcRUTaL/9iduXuOjB5ohvM11FwkbtE5C.4qHuxzazou', '张三', 'zhangsan@example.com', '13800000002', 1, 0, '普通用户示例', NOW(), NOW());

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
       (102, 1, '菜单管理', 'menu', 'system/menu/index', 'system:menu:list', 2, 'Menu', 3, 1, 1, NOW(), NOW());

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

-- ---------- 角色-菜单：admin 拥有全部 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu`;

-- ---------- 角色-菜单：common 仅有仪表盘 + 用户查询 ----------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
VALUES (2, 2), (2, 1), (2, 100), (2, 1001);
