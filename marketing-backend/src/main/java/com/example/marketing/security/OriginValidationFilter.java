package com.example.marketing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 写操作的来源校验（CSRF 纵深防御）
 * <p>
 * 非 GET/HEAD/OPTIONS 请求如果带有 Origin（或退而求其次的 Referer），其源必须在白名单内，
 * 否则直接 403。跨站表单虽然可能带上 Cookie（SameSite 失效、旧浏览器、同站兄弟源），
 * 但它的 Origin 一定不在白名单里。
 * <p>
 * 没有 Origin/Referer 的请求放行：那是 curl、服务间调用等非浏览器客户端 ——
 * 它们不构成 CSRF（CSRF 依赖受害者浏览器自动携带 Cookie）。
 * <p>
 * 白名单与 CORS 共用 {@code rbac.cors.allowed-origins}，两边不会漂移。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS =
            new HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS", "TRACE"));

    /** 允许的前端源，逗号分隔；生产环境通过 CORS_ALLOWED_ORIGINS 改为实际域名 */
    @Value("${rbac.cors.allowed-origins:http://localhost:5175}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String origin = originOf(request);
        if (origin != null && !allowedOrigins().contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"请求来源不被允许\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 优先 Origin；旧客户端只看 Referer，从中截出 scheme://host[:port] */
    private String originOf(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (StringUtils.hasText(origin)) {
            return origin.trim().toLowerCase();
        }
        String referer = request.getHeader("Referer");
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            URI uri = URI.create(referer.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            return (uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : ""))
                    .toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Set<String> allowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
