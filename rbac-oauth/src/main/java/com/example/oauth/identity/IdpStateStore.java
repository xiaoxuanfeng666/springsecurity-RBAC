package com.example.oauth.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 多 IdP 登录的 state 存储（一次性、5 分钟）
 */
@Component
@RequiredArgsConstructor
public class IdpStateStore {

    private static final String KEY_PREFIX = "oauth2:idp:state:";

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final byte[] GET_AND_DELETE = (
            "local v = redis.call('GET', KEYS[1]); "
                    + "if v then redis.call('DEL', KEYS[1]) end; "
                    + "return v"
    ).getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public String create(String provider, String returnTo) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + state,
                    objectMapper.writeValueAsString(new IdpState(provider, returnTo)), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("写入 IdP 登录 state 失败", e);
        }
        return state;
    }

    public IdpState consume(String state) {
        if (!StringUtils.hasText(state) || state.length() > 100) {
            return null;
        }
        byte[] key = (KEY_PREFIX + state).getBytes(StandardCharsets.UTF_8);
        byte[] data = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.eval(GET_AND_DELETE, ReturnType.VALUE, 1, key));
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(new String(data, StandardCharsets.UTF_8), IdpState.class);
        } catch (Exception e) {
            return null;
        }
    }
}
