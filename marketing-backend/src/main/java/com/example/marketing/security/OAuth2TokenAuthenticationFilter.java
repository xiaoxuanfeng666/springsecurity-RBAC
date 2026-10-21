package com.example.marketing.security;

import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import com.example.marketing.entity.MarketingUser;
import com.example.marketing.mapper.MarketingMenuMapper;
import com.example.marketing.mapper.MarketingUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BFF 认证过滤器
 * <p>
 * 浏览器只带一个 HttpOnly 会话 Cookie（值是 sessionId），OAuth 令牌全部在服务端：
 * <ol>
 *   <li>按 Cookie 里的 sessionId 从 Redis 取出会话（{@link BffSessionStore}），里面才有 access token；</li>
 *   <li>本地验签 access token，取出 <b>user_name</b>（见 {@link JwtTokenVerifier}）；</li>
 *   <li>access token 过期时，用会话里的 refresh token 向授权服务静默续期，
 *       新令牌写回 Redis 后本请求继续处理 —— 用户与前端都无感知；</li>
 *   <li>用 user_name 查本地的同步用户表，组装权限点后放进 SecurityContext。</li>
 * </ol>
 * 令牌无效、会话已被撤销、续期被授权服务拒绝时删除服务端会话并清掉 Cookie，下一次请求即 401。
 * <p>
 * 并发刷新是这里最需要小心的点：授权服务的一次性 refresh token 被并发提交会触发复用检测，
 * 把整个会话撤掉。因此续期前先抢 Redis 锁（{@link BffSessionStore#tryAcquireRefreshLock}），
 * 抢不到的请求等待并复用结果。
 * <p>
 * 权限点来自本地同步表，按用户缓存到 Redis（{@link LoginUserCacheStore}）：命中时整个请求零数据库查询；
 * RBAC 改了授权、MQ 一同步，消费端会清空缓存，下一次请求重新查库，因此变更仍能立即生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenAuthenticationFilter extends OncePerRequestFilter {

    /** 等并发刷新结果：50ms × 40 = 2 秒，足够覆盖一次令牌端点调用 */
    private static final long REFRESH_WAIT_INTERVAL_MS = 50;
    private static final int REFRESH_WAIT_ATTEMPTS = 40;

    private final JwtTokenVerifier jwtTokenVerifier;
    private final BffSessionStore sessionStore;
    private final BffSessionCookie sessionCookie;
    private final OAuth2TokenClient tokenClient;
    private final LoginUserCacheStore loginUserCacheStore;
    private final MarketingUserMapper userMapper;
    private final MarketingMenuMapper menuMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromSession(request, response);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateFromSession(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = sessionCookie.read(request);
        if (sessionId == null) {
            return;
        }
        BffSession session = sessionStore.get(sessionId);
        if (session == null) {
            // 浏览器还带着一个已经不存在的会话（过期、被撤权、服务端清理过）
            sessionCookie.clear(response);
            return;
        }
        // 绝对有效期每个请求都要看：刷新会拿到新的 30 分钟令牌，不卡这里的话
        // 会话可以靠不断续期越过绝对上限
        if (System.currentTimeMillis() >= session.getExpireAt()) {
            log.info("BFF 会话已达绝对有效期，要求重新登录: username={}, sessionId={}",
                    session.getUsername(), sessionId);
            sessionStore.delete(sessionId);
            sessionCookie.clear(response);
            return;
        }
        LoginUser loginUser = loadLoginUser(session, response);
        if (loginUser == null) {
            return;
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 会话里的 access token → 用户名 → 本地用户 + 权限。任一步失败返回 null，由认证入口点统一回 401。
     */
    private LoginUser loadLoginUser(BffSession session, HttpServletResponse response) {
        JwtTokenVerifier.VerifyResult result = jwtTokenVerifier.verify(session.getAccessToken());
        if (result.isExpired()) {
            result = refreshAccessToken(session, response);
            if (result == null) {
                return null;
            }
        }
        if (!result.isValid()) {
            log.info("BFF 会话的令牌无效，销毁会话: username={}, sessionId={}",
                    session.getUsername(), session.getSessionId());
            sessionStore.delete(session.getSessionId());
            sessionCookie.clear(response);
            return null;
        }
        return loadUser(result.getUsername());
    }

    /**
     * 用 refresh token 静默续期。
     * <p>
     * 返回 null 表示本次请求无法认证：会话已被判定不可用（已删除并清 Cookie），
     * 或授权服务暂时不可用（保留会话，下一次请求重试）。
     */
    private JwtTokenVerifier.VerifyResult refreshAccessToken(BffSession session, HttpServletResponse response) {
        String sessionId = session.getSessionId();
        if (!sessionStore.tryAcquireRefreshLock(sessionId)) {
            return awaitRefreshResult(session);
        }

        try {
            // 拿到锁后重读：等锁期间可能已被别的线程/实例刷新过，不能再用旧 refresh token 换一次
            BffSession current = sessionStore.get(sessionId);
            if (current == null) {
                return null;
            }
            if (!current.getAccessToken().equals(session.getAccessToken())) {
                return jwtTokenVerifier.verify(current.getAccessToken());
            }

            Map<String, Object> token = tokenClient.refreshTokenGrant(current.getRefreshToken());
            String accessToken = asString(token.get("access_token"));
            String refreshToken = asString(token.get("refresh_token"));
            if (accessToken == null) {
                log.warn("授权服务未返回 access_token，保留会话稍后重试: username={}", current.getUsername());
                return null;
            }
            current.setAccessToken(accessToken);
            if (refreshToken != null) {
                current.setRefreshToken(refreshToken);
            }
            sessionStore.save(current);
            sessionStore.saveRefreshResult(sessionId, accessToken);
            log.info("BFF 会话已静默续期: username={}, sessionId={}", current.getUsername(), sessionId);
            return jwtTokenVerifier.verify(accessToken);
        } catch (BusinessException e) {
            if (ResultCode.OAUTH_UNAVAILABLE.getCode().equals(e.getCode())) {
                log.warn("续期时授权服务不可用，保留会话稍后重试: username={}, error={}",
                        session.getUsername(), e.getMessage());
            } else {
                // invalid_grant：refresh token 过期、已被复用（盗用）或 SSO 会话已被销毁
                log.info("BFF 会话续期被拒绝，销毁会话: username={}, error={}",
                        session.getUsername(), e.getMessage());
                sessionStore.delete(sessionId);
                sessionCookie.clear(response);
            }
            return null;
        } catch (Exception e) {
            log.warn("BFF 会话续期异常，保留会话稍后重试: username={}, error={}",
                    session.getUsername(), e.getMessage());
            return null;
        } finally {
            sessionStore.releaseRefreshLock(sessionId);
        }
    }

    /**
     * 没抢到刷新锁：等待持锁请求把新令牌写进结果缓存。
     * 超时后重读会话，看是否已被别的实例刷新；都没有就放弃本次请求（会话保留）。
     */
    private JwtTokenVerifier.VerifyResult awaitRefreshResult(BffSession session) {
        String sessionId = session.getSessionId();
        for (int i = 0; i < REFRESH_WAIT_ATTEMPTS; i++) {
            try {
                Thread.sleep(REFRESH_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            String accessToken = sessionStore.getRefreshResult(sessionId);
            if (accessToken != null) {
                return jwtTokenVerifier.verify(accessToken);
            }
        }
        BffSession current = sessionStore.get(sessionId);
        if (current != null && !current.getAccessToken().equals(session.getAccessToken())) {
            return jwtTokenVerifier.verify(current.getAccessToken());
        }
        log.warn("等待并发刷新结果超时，本次请求按未认证处理: sessionId={}", sessionId);
        return null;
    }

    private LoginUser loadUser(String username) {
        // 先读缓存：命中时用户行与权限点都直接用快照，不再访问数据库
        CachedLoginUser cached = loginUserCacheStore.get(username);
        if (cached != null) {
            if (cached.getUser() == null || cached.getUser().getStatus() == null
                    || cached.getUser().getStatus() != 1) {
                loginUserCacheStore.evict(username);
                return null;
            }
            return new LoginUser(cached.getUser(), cached.getPermissions(), cached.getRoles());
        }

        MarketingUser user = userMapper.selectByUsername(username);
        if (user == null) {
            // 用户不存在于本系统的同步表：要么还没同步（RBAC 侧刚建/RocketMQ 未跑），
            // 要么已被停用/离职（下发的只含在职用户）
            log.warn("令牌有效但本地无对应用户，拒绝访问: username={}", username);
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }

        Set<String> roleKeys = splitRoleKeys(user.getRoleKeys());
        Set<String> permissions = SuperAdmin.matches(roleKeys)
                ? new HashSet<>(Collections.singletonList(SuperAdmin.WILDCARD_PERMISSION))
                : new LinkedHashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        loginUserCacheStore.save(username, new CachedLoginUser(user, roleKeys, permissions));
        return new LoginUser(user, permissions, roleKeys);
    }

    private Set<String> splitRoleKeys(String roleKeys) {
        if (roleKeys == null || roleKeys.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(roleKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
