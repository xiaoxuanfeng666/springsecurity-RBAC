package com.example.rbac.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录会话存储（Redis）
 * <p>
 * 设计目的：
 * 1. 无感知续期 —— Refresh Token 长效保存，Access 过期后凭 refresh 换取新 Access；
 * 2. 离职立刻失效 —— 删除会话键后，存量 Access 在下一次请求即被拒绝，无需等待自然过期。
 * <p>
 * Key 设计：
 * login:session:{userId}   -> 当前有效 sessionId（一个用户同时只有一个有效会话）
 * login:refresh:{sessionId} -> refresh token 原文
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String SESSION_KEY = "login:session:";
    private static final String REFRESH_KEY = "login:refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 保存会话：记录用户当前 sessionId 与其对应的 refresh token
     */
    public void saveSession(Long userId, String sessionId, String refreshToken, long ttlMillis) {
        stringRedisTemplate.opsForValue().set(SESSION_KEY + userId, sessionId, ttlMillis, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue().set(REFRESH_KEY + sessionId, refreshToken, ttlMillis, TimeUnit.MILLISECONDS);
    }

    public String getSessionId(Long userId) {
        return stringRedisTemplate.opsForValue().get(SESSION_KEY + userId);
    }

    public String getRefreshToken(String sessionId) {
        return stringRedisTemplate.opsForValue().get(REFRESH_KEY + sessionId);
    }

    /**
     * 会话是否仍然有效（sessionId 与 Redis 中记录一致）
     */
    public boolean isSessionValid(Long userId, String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return sessionId.equals(getSessionId(userId));
    }

    /**
     * 续期：刷新 Redis 中会话的过期时间（双向滑动）
     */
    public void renewSession(Long userId, String sessionId, long ttlMillis) {
        stringRedisTemplate.expire(SESSION_KEY + userId, ttlMillis, TimeUnit.MILLISECONDS);
        stringRedisTemplate.expire(REFRESH_KEY + sessionId, ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 删除会话（登出、禁用用户、删除用户、强制下线时调用）——立即失效登录凭证
     */
    public void removeSession(Long userId) {
        String sessionId = getSessionId(userId);
        stringRedisTemplate.delete(SESSION_KEY + userId);
        if (sessionId != null) {
            stringRedisTemplate.delete(REFRESH_KEY + sessionId);
        }
    }

    /**
     * 删除指定会话（登出时使用，避免误删并发的其他会话）
     */
    public void removeSession(Long userId, String sessionId) {
        removeSession(userId);
        if (sessionId != null) {
            stringRedisTemplate.delete(REFRESH_KEY + sessionId);
        }
    }
}
