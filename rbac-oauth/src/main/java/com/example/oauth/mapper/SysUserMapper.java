package com.example.oauth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.oauth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只保留本服务用到的 sys_user 访问能力：按用户名/ID 查（OAuth2 用户认证）与分页查（开放接口）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
