package com.example.audit.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 已撤销会话的集合（Redis）
 * <p>
 * 本地验签只能证明「令牌是授权服务签发的、且未过期」，证明不了「这个会话还活着」。
 * 授权服务在登出/踢人/会话轮换时广播被作废的 sid，这里记下来，验签时命中即拒。
 * <p>
 * 状态放在 Redis 而不是进程内存：本系统的多个实例共用一个 Redis，撤销记录只写一次就全体可见，
 * 因此 MQ 消费端用默认的集群模式即可（同组内只有一个实例消费，但它写的是共享状态）。
 * 相比「每请求回调授权服务」，这里只多一次 Redis EXISTS，且不需要授权服务在线。
 * <p>
 * 键按本系统隔离：多个系统共用同一个 Redis 时各写各的键，互不干扰。
 * <p>
 * <b>TTL 必须覆盖令牌剩余寿命</b>：由消息里的 {@code ttlSeconds} 决定（rbac-oauth 按会话涉及
 * 客户端的 {@code access_token_validity} 最大值计算）。写成固定值会在客户端令牌有效期更长时
 * 提前丢掉撤销记录，让已撤销令牌重新可用；只有消息未带该字段时才退回本地兜底配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationStore {

    private static final String KEY_PREFIX = "audit:sso:revoked:";

    private final StringRedisTemplate stringRedisTemplate;

    /** 兜底保留时长（秒）：仅在消息未带 ttlSeconds 时使用，须不小于本系统客户端的 access_token_validity */
    @Value("${rbac.session.revoke-ttl-seconds:43200}")
    private long defaultTtlSeconds;

    /** 记录一次撤销；重复消费同一消息无副作用 */
    public void mark(String sid, Long ttlSeconds) {
        if (!StringUtils.hasText(sid)) {
            return;
        }
        long ttl = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + sid,
                String.valueOf(System.currentTimeMillis()), ttl, TimeUnit.SECONDS);
        log.info("已记录会话撤销: sid={}, ttl={}s", sid, ttl);
    }

    public boolean isRevoked(String sid) {
        if (!StringUtils.hasText(sid)) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + sid));
    }
}
