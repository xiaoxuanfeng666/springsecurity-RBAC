package com.example.oauth.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 自助绑定的 ticket 存储
 * <p>
 * 与 state 不同，ticket 在绑定成功前可以被重复读取（用户密码输错后可以重试），
 * 成功绑定后才删除；5 分钟过期，最多尝试 5 次。
 */
@Component
@RequiredArgsConstructor
public class BindTicketStore {

    private static final String KEY_PREFIX = "oauth2:idp:bind:";

    private static final Duration TTL = Duration.ofMinutes(5);

    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    public String create(ExternalIdentity identity, String returnTo) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket,
                    objectMapper.writeValueAsString(BindTicket.from(identity, returnTo)), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("写入绑定 ticket 失败", e);
        }
        return ticket;
    }

    public BindTicket get(String ticket) {
        if (!StringUtils.hasText(ticket) || ticket.length() > 100) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + ticket);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BindTicket.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return 当前尝试次数；超过上限返回 -1 */
    public int incrementAttempts(String ticket, BindTicket current) {
        current.setAttempts(current.getAttempts() + 1);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticket,
                    objectMapper.writeValueAsString(current), TTL);
        } catch (Exception e) {
            return current.getAttempts();
        }
        return current.getAttempts() > MAX_ATTEMPTS ? -1 : current.getAttempts();
    }

    public void delete(String ticket) {
        if (StringUtils.hasText(ticket)) {
            redisTemplate.delete(KEY_PREFIX + ticket);
        }
    }
}
