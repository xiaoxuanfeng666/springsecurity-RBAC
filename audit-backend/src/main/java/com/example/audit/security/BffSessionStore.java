package com.example.audit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * BFF 会话的 Redis 存储（令牌只在这里出现，不经过浏览器）
 * <p>
 * 键按系统隔离（{@code audit:bff-session:}），多个子系统共用一个 Redis 时互不干扰。
 * TTL 取「空闲有效期」与「绝对有效期剩余」的较小值：只要登录满 7 天就一定会重新授权。
 * <p>
 * 另有两个辅助键用于<b>刷新单飞</b>：多个标签页同时遇到 access token 过期时，
 * 只有抢到锁的那个请求会真正去授权服务换令牌，其余请求等待并复用结果。
 * 否则同一张一次性 refresh token 被并发提交，会触发授权服务的复用检测、把会话整个撤掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BffSessionStore {

    private static final String KEY_PREFIX = "audit:bff-session:";
    private static final String REFRESH_LOCK_PREFIX = "audit:bff-refresh-lock:";
    private static final String REFRESH_RESULT_PREFIX = "audit:bff-refresh-result:";

    /** 刷新锁的持有时间：应大于一次令牌端点调用的超时，又不能太久导致刷新失败后长时间无法重试 */
    private static final long REFRESH_LOCK_SECONDS = 10;

    /** 刷新结果给并发等待者复用的时间：只需覆盖一两个请求的轮询窗口 */
    private static final long REFRESH_RESULT_SECONDS = 15;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 空闲有效期（秒）：会话在这段时间内没有任何刷新就会过期 */
    @Value("${rbac.bff.session-idle-ttl-seconds:604800}")
    private long idleTtlSeconds;

    public void save(BffSession session) {
        long remainingSeconds = Math.max(1, (session.getExpireAt() - System.currentTimeMillis()) / 1000);
        long ttl = Math.min(idleTtlSeconds, remainingSeconds);
        try {
            stringRedisTemplate.opsForValue().set(key(session.getSessionId()),
                    objectMapper.writeValueAsString(session), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 会话写不进去就不能算登录成功：调用方会收到异常，不会给浏览器下发 Cookie
            throw new IllegalStateException("保存 BFF 会话失败: " + e.getMessage(), e);
        }
    }

    public BffSession get(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(key(sessionId));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BffSession.class);
        } catch (Exception e) {
            log.warn("BFF 会话解析失败，按不存在处理: sessionId={}, error={}", sessionId, e.getMessage());
            delete(sessionId);
            return null;
        }
    }

    public void delete(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            stringRedisTemplate.delete(key(sessionId));
        }
    }

    /** 抢刷新锁：成功返回 true 的请求负责真正调授权服务换令牌 */
    public boolean tryAcquireRefreshLock(String sessionId) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                REFRESH_LOCK_PREFIX + sessionId, "1", REFRESH_LOCK_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseRefreshLock(String sessionId) {
        stringRedisTemplate.delete(REFRESH_LOCK_PREFIX + sessionId);
    }

    public String getRefreshResult(String sessionId) {
        return stringRedisTemplate.opsForValue().get(REFRESH_RESULT_PREFIX + sessionId);
    }

    public void saveRefreshResult(String sessionId, String accessToken) {
        stringRedisTemplate.opsForValue().set(
                REFRESH_RESULT_PREFIX + sessionId, accessToken, REFRESH_RESULT_SECONDS, TimeUnit.SECONDS);
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
