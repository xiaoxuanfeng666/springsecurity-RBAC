package com.example.marketing.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 请求上下文工具
 */
public final class WebUtils {

    private static final String UNKNOWN = "unknown";

    /** 与 sys_operation_log.ip 字段长度一致，避免超长请求头写库失败 */
    private static final int MAX_IP_LENGTH = 64;

    private WebUtils() {
    }

    /**
     * 取客户端 IP：优先反向代理头，取不到时回落到 remoteAddr。
     * 这些请求头由客户端可控，因此截断到字段长度以内。
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String ip = firstOf(request.getHeader("X-Forwarded-For"));
        if (ip == null) {
            ip = trimToNull(request.getHeader("X-Real-IP"));
        }
        if (ip == null) {
            ip = trimToNull(request.getRemoteAddr());
        }
        if (ip == null) {
            return null;
        }
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }

    public static String getRequestMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    /**
     * 取请求路径（不含查询串）。
     * <p>
     * 刻意排除查询串：像 {@code /user/{id}/resetPassword?password=xxx} 这类接口
     * 会把密码放在查询串里，记进日志等于明文留存。
     */
    public static String getRequestUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    /** X-Forwarded-For 可能是「客户端, 代理1, 代理2」，只取第一段 */
    private static String firstOf(String forwardedFor) {        String value = trimToNull(forwardedFor);
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        return trimToNull(comma > 0 ? value.substring(0, comma) : value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || UNKNOWN.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }
}
