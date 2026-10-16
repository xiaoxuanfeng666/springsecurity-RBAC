package com.example.oauth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录会话的只读校验与清理（Redis）
 * <p>
 * 会话由 rbac-backend 写入、本服务只读校验：管理端登录令牌（{@code /client/**}）能否使用，
 * 取决于那边是否还存在 {@code login:session:{userId}} 且等于令牌里的 sessionId。
 * 因此停用 / 离职 / 强制下线（那边删会话）之后，本服务持有的令牌会立即失效。
 * <p>
 * <b>键名是与 rbac-backend 的契约</b>，改动需两边同步：
 * <ul>
 *   <li>{@code login:session:{userId}} —— 当前有效 sessionId（见 rbac-backend 的 TokenStore）</li>
 *   <li>{@code login:refresh:{sessionId}} —— 该会话的 Refresh Token</li>
 *   <li>{@code login:perm:{sessionId}} —— 该会话的权限快照（见 rbac-backend 的 PermissionSnapshotStore）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SessionStore {

    private static final String SESSION_KEY = "login:session:";
    private static final String REFRESH_KEY = "login:refresh:";
    private static final String PERM_KEY = "login:perm:";

    private final StringRedisTemplate stringRedisTemplate;

    public String getSessionId(Long userId) {
        return stringRedisTemplate.opsForValue().get(SESSION_KEY + userId);
    }

    /**
     * 会话是否仍然有效（与 Redis 中记录的 sessionId 一致）
     */
    public boolean isSessionValid(Long userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return false;
        }
        return sessionId.equals(getSessionId(userId));
    }

    /**
     * 删除会话 —— 第三方经 {@code /openapi} 把用户停用或标记离职时调用，令其立即下线。
     * <p>
     * 与 rbac-backend 的 {@code TokenStore#removeSession} 保持一致：连带清掉该会话的
     * Refresh Token 与权限快照，避免它们无人引用却占着 Redis 直到 TTL 到期。
     */
    public void removeSession(Long userId) {
        String sessionId = getSessionId(userId);
        stringRedisTemplate.delete(SESSION_KEY + userId);
        if (sessionId != null) {
            stringRedisTemplate.delete(REFRESH_KEY + sessionId);
            stringRedisTemplate.delete(PERM_KEY + sessionId);
        }
    }
}
