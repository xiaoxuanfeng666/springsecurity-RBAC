package com.example.oauth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.oauth.entity.SysUser;
import com.example.oauth.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 用户信息加载（OAuth2 授权服务器）
 * <p>
 * OAuth2 的<b>密码模式</b>（{@code /oauth/token}）与<b>授权码模式的表单登录</b>（{@code /oauth/login}）
 * 都要校验本系统的用户身份，因此这里查同一张 {@code sys_user} 表 —— 与 rbac-backend 共享一个库。
 * <p>
 * 只读用户本身，<b>不加载 RBAC 权限与角色</b>：本服务不参与权限点判定。空权限集合不影响令牌签发
 * （{@code Oauth2TokenEnhancer} 只用到 userId 与昵称），签发出去的 OAuth2 令牌另按 scope 鉴权。
 * {@code LoginUser.isEnabled()} 仍在，因此停用（0）与离职（2）的用户拿不到令牌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return new LoginUser(user, null, Collections.emptySet(), Collections.emptySet());
    }
}
