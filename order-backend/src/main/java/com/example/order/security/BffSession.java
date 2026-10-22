package com.example.order.security;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * BFF 会话：浏览器只持 Cookie 里的 sessionId，OAuth 令牌全部留在服务端
 * <p>
 * 会话按 sessionId 存在 Redis（{@link BffSessionStore}）。access token 只用于本地验签，
 * refresh token 只在 access token 过期时由服务端拿去换新 —— 两者都不会下发给浏览器。
 */
@Data
public class BffSession {

    private String sessionId;

    private String username;

    private String accessToken;

    private String refreshToken;

    /** 建立会话的时间（毫秒） */
    private long loginTime;

    /** 绝对过期时间（毫秒）：到点必须重新走一次授权，与空闲过期相互独立 */
    private long expireAt;

    /** 登录时客户端 User-Agent 的归一化摘要：Cookie 被复制到别的客户端时据此拒绝 */
    private String clientFingerprint;

    /** 二次验证有效截止时间（毫秒时间戳）；0 表示尚未验证 */
    private long elevatedUntil;


    /** 日志里只保留前 8 位，避免会话 id 通过日志泄漏后被直接复用 */
    public static String mask(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        return sessionId.length() <= 8 ? "****" : sessionId.substring(0, 8) + "...";
    }
}
