package com.example.oauth.oauth;

import com.example.oauth.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth2 访问令牌认证过滤器（供第三方调用 {@code /openapi/**}）
 * <p>
 * 第三个系统拿到本系统签发的 access token 后，用 {@code Authorization: Bearer <token>}
 * 调 {@code /openapi/**}。本过滤器负责把该令牌变成 Spring Security 的认证对象：
 * <ul>
 *   <li>用 {@code JwtTokenStore} 校验签名与有效期 —— 令牌由本系统签发，验签用同一把密钥（keystore）；</li>
 *   <li>把令牌的 scope 映射成 {@code SCOPE_xxx} 权限，供 {@code hasAuthority("SCOPE_read")} 判权；</li>
 *   <li>身份放进 {@link OpenApiCaller}，<b>不加载 RBAC 权限</b> —— 这条路径与「本系统登录用户」完全隔离。</li>
 * </ul>
 * <p>
 * 与 {@code OauthJwtAuthenticationFilter} 的关键区别：后者要求令牌带 {@code tokenType=access} 且
 * 在 Redis 有 {@code login:session:*} 会话（rbac-backend 签发的登录令牌），而 OAuth2 令牌两者都没有。
 * 两者互补：本过滤器只装在 {@code /openapi/**} 链上，不会让 OAuth2 令牌流到管理接口。
 * <p>
 * 校验失败不抛异常，直接放行交给本链的入口点返回 401 —— 避免把令牌细节泄露给调用方。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiTokenAuthenticationFilter extends OncePerRequestFilter {

    /** scope 前缀，与 Spring Security 的惯例一致，便于用 hasAuthority("SCOPE_read") 判权 */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private static final String CLAIM_USER_NAME = "user_name";

    private final TokenStore jwtTokenStore;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String tokenValue, HttpServletRequest request) {
        OAuth2AccessToken accessToken;
        OAuth2Authentication oauth2Authentication;
        try {
            accessToken = jwtTokenStore.readAccessToken(tokenValue);
            // 客户端标识不能从 accessToken.getAdditionalInformation() 取：
            // JwtAccessTokenConverter.extractAccessToken() 会把 client_id / scope / exp / aud
            // 从附加信息里剔除，只能从重建出来的 OAuth2 认证对象上拿。
            oauth2Authentication = jwtTokenStore.readAuthentication(tokenValue);
        } catch (Exception e) {
            // 签名不符、已过期、格式非法等一律视为未认证
            log.debug("OAuth2 令牌校验未通过: {}", e.getMessage());
            return;
        }
        if (accessToken == null || oauth2Authentication == null) {
            return;
        }

        Map<String, Object> claims = accessToken.getAdditionalInformation();
        String clientId = oauth2Authentication.getOAuth2Request().getClientId();
        // 用户类授权模式（授权码/密码）才有 user_name；客户端凭证模式没有用户，退化为客户端 id
        String userName = asString(claims.get(CLAIM_USER_NAME));
        String subject = StringUtils.hasText(userName) ? userName : clientId;

        Set<String> scopes = accessToken.getScope() == null
                ? Collections.emptySet() : new LinkedHashSet<>(accessToken.getScope());
        List<GrantedAuthority> authorities = scopes.stream()
                .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope))
                .collect(Collectors.toList());

        OpenApiCaller caller = new OpenApiCaller(subject, clientId, scopes);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(caller, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("第三方调用方已认证: subject={}, clientId={}, scopes={}", subject, clientId, scopes);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getTokenPrefix();
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length()).trim();
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
