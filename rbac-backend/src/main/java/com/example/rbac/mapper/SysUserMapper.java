package com.example.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rbac.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户的角色标识列表（admin / common ...）
     */
    @Select("SELECT r.role_key FROM sys_role r " +
            "JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.del_flag = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);
}
