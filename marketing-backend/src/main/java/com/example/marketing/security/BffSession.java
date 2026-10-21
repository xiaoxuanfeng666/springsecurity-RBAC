package com.example.marketing.security;

import lombok.Data;

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
}
