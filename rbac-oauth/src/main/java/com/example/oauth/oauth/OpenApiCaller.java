package com.example.oauth.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.Set;

/**
 * 调用 {@code /openapi/**} 的第三方身份（由 OAuth2 访问令牌解析而来）
 * <p>
 * 与 {@link com.example.oauth.security.LoginUser} 刻意分开：后者代表「本系统登录用户」，
 * 携带 RBAC 权限快照；本类代表「持 OAuth2 令牌的第三方调用方」，只有 scope，
 * 不参与 RBAC 权限判定。
 * <p>
 * 实现 {@link Principal} 是为了让认证对象的 {@code getName()} 返回有意义的值
 * （{@code UsernamePasswordAuthenticationToken} 对非 Principal/UserDetails 的主体
 * 会退化为 {@code toString()}）。
 */
@Getter
@RequiredArgsConstructor
public class OpenApiCaller implements Principal {

    /**
     * 令牌主体：用户类授权模式（授权码/密码）下是用户名；
     * 客户端凭证模式下没有用户，取客户端 id
     */
    private final String subject;

    /** 客户端标识，来自令牌的 client_id 声明 */
    private final String clientId;

    /** 令牌授予的 scope 集合 */
    private final Set<String> scopes;

    @Override
    public String getName() {
        return subject;
    }
}
