-- ============================================================
-- 数据修复：释放被逻辑删除行占用的业务唯一键
--
-- 背景：删除走逻辑删除（del_flag=1），行仍物理留在表里，唯一键也就仍被占用。
-- 结果是「删掉用户后，同名用户名再也建不出来」——应用层重名校验排除了已删行所以放行，
-- 随后 INSERT 撞上数据库唯一键，前端只看到 code=500 系统异常。
-- 影响 sys_user.username / sys_role.role_key / sys_post.post_code 三处。
--
-- 代码侧已改为「删除时把唯一字段改名为 原名_deleted_{id}」，本脚本把存量已删行补上同样的改名。
-- 可重复执行：已带 _deleted_ 标记的行会被跳过。
-- ============================================================

USE `rbac`;

UPDATE `sys_user`
SET `username` = CONCAT(LEFT(`username`, 64 - CHAR_LENGTH(CONCAT('_deleted_', `id`))),
                        '_deleted_', `id`)
WHERE `del_flag` = 1
  AND `username` NOT LIKE '%\_deleted\_%';

UPDATE `sys_role`
SET `role_key` = CONCAT(LEFT(`role_key`, 64 - CHAR_LENGTH(CONCAT('_deleted_', `id`))),
                        '_deleted_', `id`)
WHERE `del_flag` = 1
  AND `role_key` NOT LIKE '%\_deleted\_%';

UPDATE `sys_post`
SET `post_code` = CONCAT(LEFT(`post_code`, 64 - CHAR_LENGTH(CONCAT('_deleted_', `id`))),
                         '_deleted_', `id`)
WHERE `del_flag` = 1
  AND `post_code` NOT LIKE '%\_deleted\_%';
