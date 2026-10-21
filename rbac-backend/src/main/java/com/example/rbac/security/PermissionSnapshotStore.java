package com.example.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 权限快照存储（Redis）
 * <p>
 * Key：{@code login:perm:user:{username}} -> 该用户的权限快照 JSON。
 * <p>
 * 快照在首次请求（登录换令牌）时建立，之后每个请求直接读取，省掉「用户-角色-菜单」的三表联查。
 * 角色授权或用户资料/角色变更时按 userId 精准失效，用户下一次请求即重建并拿到新权限。
 * 用户被停用/离职时，SSO 会话由 rbac-oauth 删除，令牌校验本身就会失败，快照不再被读到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionSnapshotStore {

    private static final String KEY_PREFIX = "login:perm:user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SysUserMapper userMapper;

    static String key(String username) {
        return KEY_PREFIX + username;
    }

    /**
     * 读取快照，不存在或反序列化失败返回 null（调用方回退为查库重建）
     */
    public PermissionSnapshot get(String username) {
        if (username == null) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(key(username));
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

    public void save(String username, PermissionSnapshot snapshot, long ttlMillis) {
        if (username == null || snapshot == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key(username),
                    objectMapper.writeValueAsString(snapshot), ttlMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("权限快照写入失败，本次请求直接使用查库结果: {}", e.getMessage());
        }
    }

    public void invalidateUser(Long userId) {
        invalidateUsers(Collections.singletonList(userId));
    }

    /**
     * 批量失效用户快照：先由 userId 查到 username，再删除对应快照。
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
            SysUser user = userMapper.selectById(userId);
            if (user != null && user.getUsername() != null) {
                stringRedisTemplate.delete(key(user.getUsername()));
                affected++;
            }
        }
        log.info("权限快照已失效: 用户数={}, 命中={}", userIds.size(), affected);
    }
}
