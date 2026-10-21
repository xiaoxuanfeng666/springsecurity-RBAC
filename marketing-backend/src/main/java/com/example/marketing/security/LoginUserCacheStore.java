package com.example.marketing.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 登录用户 / 权限的 Redis 缓存（读穿）
 * <p>
 * 每个请求都按 username 查用户行、再按 userId 关联菜单表算权限点，是典型的「读多写极少」。
 * 这里把「用户 + 角色 + 权限点」整体缓存，命中时零数据库查询。
 * <p>
 * 失效由 RBAC 的同步消息驱动：权限/角色/菜单/用户任何变更都会触发全量重建，
 * 消费端在重建完成（事务提交）后调用 {@link #evictAll()} 清空本系统命名空间下的缓存，
 * 下一次请求重新查库。TTL 只是兜底，防止消息丢失时缓存永久陈旧。
 * <p>
 * 键按系统隔离（{@code marketing:auth:user:}），多系统共用一个 Redis 时互不干扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserCacheStore {

    private static final String KEY_PREFIX = "marketing:auth:user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 兜底有效期（秒）：正常失效靠 MQ 清除 */
    @Value("${rbac.auth-cache-ttl-seconds:300}")
    private long ttlSeconds;

    public CachedLoginUser get(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + username);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CachedLoginUser.class);
        } catch (Exception e) {
            // 格式不对（历史版本、被写坏）时按未命中处理，并删掉脏键
            log.warn("登录用户缓存解析失败，按未命中处理: username={}, error={}", username, e.getMessage());
            evict(username);
            return null;
        }
    }

    public void save(String username, CachedLoginUser value) {
        if (!StringUtils.hasText(username) || value == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + username,
                    objectMapper.writeValueAsString(value), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 缓存写失败不影响本次请求，只是下一次仍走数据库
            log.warn("写登录用户缓存失败: username={}, error={}", username, e.getMessage());
        }
    }

    public void evict(String username) {
        if (StringUtils.hasText(username)) {
            stringRedisTemplate.delete(KEY_PREFIX + username);
        }
    }

    /**
     * 清空本系统的全部用户缓存。
     * <p>
     * RBAC 的同步消息是全量数据（菜单、角色菜单、用户、用户角色），任何一项变化都可能影响
     * 任意用户的权限，所以直接整体失效，不做逐个推断。用 SCAN 而非 KEYS，避免阻塞 Redis。
     */
    public void evictAll() {
        Set<String> keys = new LinkedHashSet<>();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (keys.isEmpty()) {
            return;
        }
        stringRedisTemplate.delete(keys);
        log.info("已清空登录用户缓存: {} 个", keys.size());
    }
}
