package com.example.audit.security;

import com.example.audit.entity.AuditUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Spring Security 登录用户
 * <p>
 * 注意本系统不做登录：这个对象是**每个请求**由 OAuth2 令牌换来的用户名重建的，
 * 权限来自本地同步表（audit_role_menu → audit_menu.perms），不是令牌里的内容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private AuditUser user;

    /** 权限标识集合，如 audit:log:list */
    private Set<String> permissions;

    /** 角色标识集合，如 admin */
    private Set<String> roles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(
                        permissions == null ? Stream.empty() : permissions.stream(),
                        roles == null ? Stream.empty() : roles.stream().map(r -> "ROLE_" + r))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /** 本系统不校验密码：身份已由授权服务器验证过 */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
