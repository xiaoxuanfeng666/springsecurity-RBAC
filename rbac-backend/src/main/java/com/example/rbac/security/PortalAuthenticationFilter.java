package com.example.rbac.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * 门户接口链的令牌认证过滤器
 * <p>
 * 与 {@link JwtAuthenticationFilter}（管理端）的关键区别：这里认的是 <b>OAuth2 令牌</b>，
 * 而不是本服务自己签发的登录令牌；身份从授权服务的 {@code /oauth/check_token} 换回来，
 * 且<b>不加载任何 RBAC 权限</b>（门户接口不需要权限点）。
 * <p>
 * 两条链各自只认自己那套凭证，因此门户令牌拿不到任何管理端接口，管理端令牌也进不了门户接口。
 * 校验失败不抛异常、也不写响应，直接放行交给本链的入口点回 401 —— 避免把令牌细节泄露给调用方。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortalAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PortalTokenClient portalTokenClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            PortalUser user = portalTokenClient.checkToken(token);
            if (user != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
