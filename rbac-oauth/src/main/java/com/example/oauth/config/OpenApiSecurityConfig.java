package com.example.oauth.config;

import com.example.oauth.oauth.OpenApiTokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方开放接口（{@code /openapi/**}）的安全链
 * <p>
 * 与管理接口链完全隔离：这里的调用方持 <b>OAuth2 访问令牌</b>，只按 <b>scope</b> 粗粒度鉴权，
 * <b>不参与 RBAC 权限点判定</b>；管理接口则只认 rbac-backend 签发的登录令牌（JWT + Redis 会话）。
 * 两条链的令牌互不通用 —— OAuth2 令牌调不了管理接口，登录令牌也进不来这里。
 * <p>
 * 链顺序：1 令牌端点 / 2 授权端点登录 / <b>3 本链</b> / 4 管理接口（登录令牌）。
 * <p>
 * 鉴权规则刻意用「白名单 + 兜底拒绝」：新加的 {@code /openapi} 接口必须显式放行，
 * 否则默认拒绝，避免新增接口时忘了设权限。
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiSecurityConfig {

    private static final String SCOPE_READ = "SCOPE_read";
    private static final String SCOPE_WRITE = "SCOPE_write";

    private final OpenApiTokenAuthenticationFilter openApiTokenAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    @Order(3)
    public SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
        http
                .requestMatchers()
                    .antMatchers("/openapi/**")
                .and()
                .authorizeRequests()
                    // 读自己的身份：任意有效令牌都行，便于第三方拿到令牌后自检
                    .antMatchers("/openapi/userinfo").authenticated()
                    // 粗粒度：只认 scope，不认 system:xxx:yyy 这类 RBAC 权限点
                    .antMatchers(HttpMethod.GET, "/openapi/**").hasAuthority(SCOPE_READ)
                    .antMatchers(HttpMethod.PUT, "/openapi/**").hasAuthority(SCOPE_WRITE)
                    // 白名单之外一律拒绝：新增接口必须显式放行，避免漏配权限
                    .anyRequest().denyAll()
                .and()
                .exceptionHandling()
                    // 面向第三方，返回 OAuth2 风格错误（而非本系统那套「登录已过期」）
                    .authenticationEntryPoint((request, response, ex) ->
                            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                    "invalid_token", "访问令牌缺失或无效"))
                    .accessDeniedHandler((request, response, ex) ->
                            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                    "insufficient_scope", "令牌的 scope 不足"))
                .and()
                .csrf().disable()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilterBefore(openApiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String error, String description)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + error + "\"");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
