package com.example.rbac.security;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 客户端指纹（软绑定）
 * <p>
 * 登录时记录 User-Agent 的归一化摘要，之后每个请求比对；不一致说明会话 Cookie
 * 被复制到了另一个客户端，直接销毁会话。
 * <p>
 * 归一化是为了避免浏览器自动升级导致误杀：把所有版本号数字替换成同一个占位符。
 * 注意这只是提高门槛，不是安全边界 —— UA 不是秘密，木马可以同时拿到。
 */
public final class BffClientFingerprint {

    private static final String UNKNOWN = "unknown";

    private BffClientFingerprint() {
    }

    public static String of(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return UNKNOWN;
        }
        String normalized = userAgent.replaceAll("\\d+(?:\\.\\d+)*", "#");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
