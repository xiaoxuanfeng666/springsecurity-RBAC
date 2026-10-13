package com.example.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 权限快照存储（Redis）
 * <p>
 * Key：{@code login:perm:{sessionId}} -> 该会话的权限快照 JSON。
 * <p>
 * 快照在登录时建立，之后每个请求直接读取，省掉「用户-角色-菜单」的三表联查。
 * 两种失效时机：
 * <ul>
 *   <li>会话删除（登出 / 停用 / 离职 / 强制下线 / 重置密码）——由 {@link TokenStore} 一并清理；</li>
 *   <li>角色授权或用户角色变更——按 userId 精准失效，用户下一次请求即重建并拿到新权限。</li>
 * </ul>
 * 由于 sessionId 每次登录都会重新生成，失效后的旧快照不可能再被读到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionSnapshotStore {

    private static final String KEY_PREFIX = "login:perm:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenStore tokenStore;

    /** 供 TokenStore 删除会话时清理同一 sessionId 的快照 */
    static String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    /**
     * 读取快照，不存在或反序列化失败返回 null（调用方回退为查库重建）
     */
    public PermissionSnapshot get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(key(sessionId));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PermissionSnapshot.class);
        } catch (Exception e) {
            log.warn("权限快照反序列化失败，本次回退为查库重建: {}", e.getMessage());
            return null;
        }
    }

    public void save(String sessionId, PermissionSnapshot snapshot, long ttlMillis) {
        if (sessionId == null || snapshot == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key(sessionId),
                    objectMapper.writeValueAsString(snapshot), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("权限快照写入失败，本次请求直接使用查库结果: {}", e.getMessage());
        }
    }

    public void invalidateUser(Long userId) {
        invalidateUsers(Collections.singletonList(userId));
    }

    /**
     * 批量失效用户快照：先由 userId 找到其当前 sessionId，再删除该会话的快照。
     * 用户下一次请求读不到快照，会重新查库并重建。
     */
    public void invalidateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        int affected = 0;
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            String sessionId = tokenStore.getSessionId(userId);
            if (sessionId != null) {
                stringRedisTemplate.delete(key(sessionId));
                affected++;
            }
        }
        log.info("权限快照已失效: 用户数={}, 命中在线会话={}", userIds.size(), affected);
    }
}
