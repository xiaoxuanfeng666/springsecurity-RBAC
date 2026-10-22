package com.example.audit.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * BFF 会话 Cookie：浏览器里唯一与登录相关的东西
 * <p>
 * 值是随机的 sessionId，不是令牌；HttpOnly 保证脚本读不到（XSS 也偷不走服务端令牌）。
 * Cookie 名按系统区分，避免多个子系统部署在同一域名下时互相覆盖。
 * <p>
 * Servlet 4.0 的 Cookie 不支持 SameSite 属性，直接拼 Set-Cookie 头。
 * 开发环境是 http，加了 Secure 浏览器会丢弃 Cookie；生产走 HTTPS 时把
 * {@code rbac.bff.cookie-secure} 配成 true。
 */
@Component
public class BffSessionCookie {

    @Value("${rbac.bff.cookie-name:audit_bff_session}")
    private String cookieName;

    @Value("${rbac.bff.cookie-secure:false}")
    private boolean secure;

    @Value("${rbac.bff.session-max-ttl-seconds:43200}")
    private int maxAgeSeconds;

    public void write(HttpServletResponse response, String sessionId) {
        addCookie(response, sessionId, maxAgeSeconds);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void clear(HttpServletResponse response) {
        addCookie(response, "", 0);
    }

    private void addCookie(HttpServletResponse response, String value, int maxAge) {
        StringBuilder header = new StringBuilder(cookieName).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAge);
        if (secure) {
            header.append("; Secure");
        }
        response.addHeader("Set-Cookie", header.toString());
    }
}
