package com.example.rbac.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 生成与解析
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_SESSION_ID = "sessionId";

    /** Token 类型 */
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final Key key;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public static String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 Access Token（短效，携带 sessionId 以便校验会话是否仍有效）
     */
    public String createAccessToken(Long userId, String username, String sessionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpire());
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim("type", TYPE_ACCESS)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成 Refresh Token（长效）
     */
    public String createRefreshToken(Long userId, String sessionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpire());
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim("type", TYPE_REFRESH)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token，失败抛 JwtException
     */
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 Token 是否有效（签名正确且未过期）
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 无效: {}", e.getMessage());
        }
        return false;
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getSessionId(Claims claims) {
        return claims.get(CLAIM_SESSION_ID, String.class);
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get("type", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get("type", String.class));
    }

    public long getAccessTokenExpireSeconds() {
        return jwtProperties.getAccessTokenExpire() / 1000;
    }

    public long getRefreshTokenExpireMillis() {
        return jwtProperties.getRefreshTokenExpire();
    }
}
