package com.example.rbac.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录会话存储（Redis）
 * <p>
 * 会话是真正的登录态载体，「刷新 Token 接口」只是换发新的 Access Token，
 * 而能否继续登录由会话是否存在决定 —— 因此停用/离职用户删除会话后立即失效。
 * <p>
 * Key：
 * <ul>
 *   <li>{@code login:session:{userId}} -> 当前有效 sessionId（一个用户同时只有一个有效会话）</li>
 *   <li>{@code login:refresh:{sessionId}} -> 该会话的 Refresh Token，用于校验刷新请求</li>
 *   <li>{@code login:perm:{sessionId}} -> 该会话的权限快照，见 {@link PermissionSnapshotStore}</li>
 * </ul>
 * <p>
 * 除了管理端自己的登录态，本类还负责连带清除该用户的 <b>SSO 会话</b>（{@code sso:*}，见
 * {@link SsoSessionStore}）—— 那是门户与各子系统的登录态。两者是不同的东西：
 * 前者是本后台的 JWT 会话，后者是 OAuth2 令牌的存活开关；但「让这个人下线」这件事必须同时做到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String SESSION_KEY = "login:session:";
    private static final String REFRESH_KEY = "login:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SsoSessionStore ssoSessionStore;

    /**
     * 保存会话：用户维度记录当前 sessionId，会话维度记录 Refresh Token
     * <p>
     * 单会话模型下新登录会顶掉旧会话，这里顺带删掉上一个 sessionId 的
     * Refresh Token 与权限快照，避免它们无人引用却占着 Redis 直到 TTL 到期。
     */
    public void saveSession(Long userId, String sessionId, String refreshToken, long ttlMillis) {
        String previousSessionId = getSessionId(userId);
        if (previousSessionId != null && !previousSessionId.equals(sessionId)) {
            stringRedisTemplate.delete(REFRESH_KEY + previousSessionId);
            stringRedisTemplate.delete(PermissionSnapshotStore.key(previousSessionId));
        }
        stringRedisTemplate.opsForValue()
                .set(SESSION_KEY + userId, sessionId, ttlMillis, TimeUnit.MILLISECONDS);
        stringRedisTemplate.opsForValue()
                .set(REFRESH_KEY + sessionId, refreshToken, ttlMillis, TimeUnit.MILLISECONDS);
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
     * 删除会话（登出、停用/离职用户、删除用户、强制下线、重置密码）——立即失效登录凭证。
     * 同时删除该会话的 Refresh Token 与权限快照，使旧刷新凭证无法续期、快照不再被读到。
     * <p>
     * <b>这里也是「统一登出」在管理端的唯一落点</b>：一并清掉该用户的 SSO 会话，
     * 让他在门户与各子系统里的令牌同时失效。之所以放在这个方法里而不是散到各个调用处 ——
     * 「removeSession」的语义本来就是「让这个人下线」，而上面这一串场景（停用、离职、删除、
     * 重置密码、强制下线、主动登出）全都应当连带把他从所有子系统踢出去；
     * 但凡漏掉一处，就会出现「账号都停用了，子系统里还进得去」的漏洞。
     */
    public void removeSession(Long userId) {
        String sessionId = getSessionId(userId);
        stringRedisTemplate.delete(SESSION_KEY + userId);
        if (sessionId != null) {
            stringRedisTemplate.delete(REFRESH_KEY + sessionId);
            stringRedisTemplate.delete(PermissionSnapshotStore.key(sessionId));
        }
        if (ssoSessionStore.removeByUserId(userId)) {
            log.info("已同步清除 SSO 会话，该用户在各子系统的令牌即刻失效: userId={}", userId);
        }
    }
}
