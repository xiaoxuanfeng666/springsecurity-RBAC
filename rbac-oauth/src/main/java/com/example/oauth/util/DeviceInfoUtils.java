package com.example.oauth.util;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 从登录请求里取「谁、从哪台设备登录的」
 * <p>
 * 只做轻量解析，不引入 UA 解析库：够在会话列表里一眼认出「Chrome / Windows」还是「curl」即可。
 * 原始 User-Agent 也会一并存下来，将来要更精确的分析时不必重新采集。
 */
public final class DeviceInfoUtils {

    private static final int MAX_USER_AGENT = 512;

    private DeviceInfoUtils() {
    }

    /**
     * 登录 IP：反向代理后面要优先取 {@code X-Forwarded-For} 的第一段，否则拿到的是网关地址
     */
    public static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        return ua.length() > MAX_USER_AGENT ? ua.substring(0, MAX_USER_AGENT) : ua;
    }

    /**
     * 把 User-Agent 归纳成「浏览器 / 操作系统」，认不出的信息也保留原样
     */
    public static String describe(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "未知客户端";
        }
        return browserOf(userAgent) + " / " + osOf(userAgent);
    }

    private static String browserOf(String ua) {
        // Edge 的 UA 里同时带 Chrome 与 Safari，必须先判断
        if (ua.contains("Edg/") || ua.contains("Edge/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        if (ua.contains("PostmanRuntime")) {
            return "Postman";
        }
        if (ua.startsWith("curl/") || ua.contains("curl/")) {
            return "curl";
        }
        if (ua.contains("python") || ua.contains("Python")) {
            return "Python";
        }
        if (ua.contains("Java/") || ua.startsWith("Apache-HttpClient")) {
            return "Java 客户端";
        }
        return "其他客户端";
    }

    private static String osOf(String ua) {
        if (ua.contains("Windows NT 10.0")) {
            return "Windows 10/11";
        }
        if (ua.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (ua.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "未知系统";
    }
}
