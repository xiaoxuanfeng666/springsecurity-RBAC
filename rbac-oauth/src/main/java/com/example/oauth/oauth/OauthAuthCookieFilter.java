package com.example.oauth.oauth;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.OauthAuthStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 把「浏览器 Cookie 里的登录态」还原成 Spring Security 的认证对象
 * <p>
 * 授权码 / 隐式模式的登录态不再放在 HTTP 会话里（那样会被 Spring Session 序列化成一大团
 * Java 对象），而是 {@link OauthAuthStore} 里的一条 JSON。本过滤器是读的那一端：
 * 取 Cookie → 查 Redis → 命中就把 {@code LoginUser} 放进 SecurityContext，
 * 后续 {@code /oauth/authorize} 便认为用户已登录。
 * <p>
 * 与 {@code OAuth2TokenEndpointSecurityConfig} 的客户端认证链互不相干：那条链是无状态的，
 * 本过滤器只装在授权端点链（{@code @Order(2)}）上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OauthAuthCookieFilter extends OncePerRequestFilter {

    private final OauthAuthStore oauthAuthStore;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = OauthAuthStore.readToken(request);
            LoginUser loginUser = oauthAuthStore.read(token, request);
            if (loginUser != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
