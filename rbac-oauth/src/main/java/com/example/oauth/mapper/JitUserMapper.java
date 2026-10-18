package com.example.oauth.mapper;

import com.example.oauth.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * JIT 自动建号用到的写入操作（rbac-oauth 与 rbac-backend 共用 sys_user 库）
 */
@Mapper
public interface JitUserMapper {

    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Select("SELECT id FROM sys_role WHERE role_key = #{roleKey} AND status = 1 AND del_flag = 0 LIMIT 1")
    Long selectRoleIdByKey(@Param("roleKey") String roleKey);

    @Insert("INSERT INTO sys_user (username, password, nickname, dept_id, email, phone, status, remark, create_time, update_time) "
            + "VALUES (#{username}, #{password}, #{nickname}, #{deptId}, #{email}, #{phone}, 1, #{remark}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(SysUser user);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
