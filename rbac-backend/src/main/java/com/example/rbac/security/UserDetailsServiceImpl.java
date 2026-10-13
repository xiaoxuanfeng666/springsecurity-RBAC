package com.example.rbac.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 用户信息加载
 * <p>
 * 注意：权限每次请求都从数据库联表查询（不缓存快照），
 * 因此管理员修改「角色-权限」后，用户下一次请求立即生效。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return buildLoginUser(user, null);
    }

    /**
     * 按 userId 加载（供 JWT 过滤器使用）
     */
    public LoginUser loadLoginUser(Long userId, String sessionId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return buildLoginUser(user, sessionId);
    }

    private LoginUser buildLoginUser(SysUser user, String sessionId) {
        Set<String> permissions = new HashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        Set<String> roles = new HashSet<>(userMapper.selectRoleKeysByUserId(user.getId()));
        return new LoginUser(user, sessionId, permissions, roles);
    }
}
