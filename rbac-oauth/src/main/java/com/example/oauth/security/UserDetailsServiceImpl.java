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
        // del_flag 由 @TableLogic 自动附加（实际 SQL 为 WHERE del_flag=0 AND username=?）。
        // 这里刻意**不**过滤 status：必须把停用/离职的行读出来，交给框架的账号状态检查
        // （登录走 DaoAuthenticationProvider，刷新走 PreAuthenticatedAuthenticationProvider
        // 默认的 AccountStatusUserDetailsChecker）抛 DisabledException，
        // 前端才能提示「账号已停用」；若在 SQL 里加 status=1，会退化成笼统的「用户名或密码错误」。
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        return new LoginUser(user, null, Collections.emptySet(), Collections.emptySet());
    }
}
