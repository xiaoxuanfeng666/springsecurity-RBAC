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
 * 1. 解析 Access Token；
 * 2. 校验 Redis 中的会话是否仍然有效 —— 用户被禁用/删除/强制下线后，会话键被删除，
 * 存量 Access Token 立即失效（满足「离职立刻失效」）；
 * 3. 实时加载用户权限写入 SecurityContext（满足「动态修改角色权限」）。
 * <p>
 * 任一环节失败都不设置认证信息，请求以未认证状态继续，最终由 EntryPoint 返回 401，
 * 前端据此触发 refresh 流程。
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
            try {
                Claims claims = jwtTokenProvider.parse(token);
                if (jwtTokenProvider.isAccessToken(claims)) {
                    Long userId = jwtTokenProvider.getUserId(claims);
                    String sessionId = jwtTokenProvider.getSessionId(claims);

                    // 会话校验：用户离职/被踢下线后此处立即失败
                    if (tokenStore.isSessionValid(userId, sessionId)) {
                        LoginUser loginUser = userDetailsService.loadLoginUser(userId, sessionId);
                        if (loginUser != null && loginUser.isEnabled()) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            loginUser, null, loginUser.getAuthorities());
                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            } catch (Exception e) {
                // Token 过期/无效：不设置认证，交由 EntryPoint 返回 401
                log.debug("JWT 认证失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
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
