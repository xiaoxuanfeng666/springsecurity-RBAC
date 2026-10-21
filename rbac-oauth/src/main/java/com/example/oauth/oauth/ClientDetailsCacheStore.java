package com.example.oauth.oauth;

import com.example.oauth.entity.ClientDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 客户端配置缓存（Redis）
 * <p>
 * Key：{@code oauth2:client:{clientId}} -> clientdetails 行的 JSON（appSecret 仍是 AES 密文）。
 * <p>
 * 令牌端点每次请求都要加载客户端，而客户端配置几乎不变，因此先读这里、未命中再回源 MySQL。
 * 缓存只是加速，正确性仍由数据库保证：管理端编辑/删除客户端后经 RocketMQ 发来
 * {@code RBAC_CLIENT_CHANGE}，由 {@link com.example.oauth.mq.ClientChangeConsumer} 删掉对应键，
 * 下一次加载即回源拿到新配置；TTL 只是兜底，防止消息丢失时缓存永远陈旧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientDetailsCacheStore {

    private static final String KEY_PREFIX = "oauth2:client:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rbac.client.cache-ttl-seconds:3600}")
    private long ttlSeconds;

    static String key(String clientId) {
        return KEY_PREFIX + clientId;
    }

    /** 读取缓存；未命中或反序列化失败返回 null（调用方回源查库） */
    public ClientDetails get(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(key(clientId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ClientDetails.class);
        } catch (Exception e) {
            log.warn("客户端缓存反序列化失败，本次回源查库: clientId={}, {}", clientId, e.getMessage());
            return null;
        }
    }

    /** 写入缓存；失败只记日志，不影响本次请求（本次直接用查库结果） */
    public void save(ClientDetails client) {
        if (client == null || !StringUtils.hasText(client.getAppId())) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key(client.getAppId()),
                    objectMapper.writeValueAsString(client), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("客户端缓存写入失败，本次请求直接使用查库结果: clientId={}, {}",
                    client.getAppId(), e.getMessage());
        }
    }

    /** 失效缓存；键不存在也无妨，消息可能重投，天然幂等 */
    public void evict(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return;
        }
        stringRedisTemplate.delete(key(clientId));
    }
}
