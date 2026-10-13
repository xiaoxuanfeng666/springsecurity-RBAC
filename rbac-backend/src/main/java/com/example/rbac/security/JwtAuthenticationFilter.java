package com.example.rbac.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * JWT 认证过滤器
 * <p>
 * 每次请求：
 * 1. 解析 Access Token（仅接受 access 类型，Refresh Token 不能当访问凭证）；
 * 2. 校验 Redis 会话 —— 用户停用/离职/被删除/强制下线后会话被删除，存量 Token 立即失效；
 * 3. 实时加载用户权限写入 SecurityContext（权限变更立即生效）。
 * <p>
 * Token 过期不做自动续期，由前端拦截 401 后调用 /auth/refresh 换取新 Access Token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenStore tokenStore;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return;
        }
        if (!jwtTokenProvider.isAccessToken(claims)) {
            return;
        }

        Long userId = jwtTokenProvider.getUserId(claims);
        String sessionId = jwtTokenProvider.getSessionId(claims);

        // 会话校验：用户离职/被踢下线后此处立即失败
        if (!tokenStore.isSessionValid(userId, sessionId)) {
            return;
        }
        LoginUser loginUser = userDetailsService.loadLoginUser(userId, sessionId);
        if (loginUser == null || !loginUser.isEnabled()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(jwtProperties.getHeader());
        String prefix = jwtProperties.getTokenPrefix();
        if (StringUtils.hasText(bearer) && bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length()).trim();
        }
        return null;
    }
}
