package com.example.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.audit.entity.AuditUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditUserMapper extends BaseMapper<AuditUser> {

    /**
     * 按登录名查<b>在职</b>用户（status = 1）
     * <p>
     * 登录名是 OAuth2 令牌里唯一可靠的身份（令牌中只有 user_name 声明），
     * 因此每个请求都靠它把令牌映射到本地同步过来的用户行。
     * <p>
     * 条件必须带 {@code status = 1}：停用(0)/离职(2) 的账号即使手里还有未过期的令牌，
     * 也不该再能访问本系统。同步表可能保留这些行，查询层直接把它们当作不存在。
     */
    @Select("SELECT * FROM audit_user WHERE username = #{username} AND status = 1")
    AuditUser selectByUsername(@Param("username") String username);
}
