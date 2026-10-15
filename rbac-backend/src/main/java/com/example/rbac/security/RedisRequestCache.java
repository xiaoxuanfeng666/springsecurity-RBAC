package com.example.rbac.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.savedrequest.SimpleSavedRequest;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 基于 Redis 的请求缓存（供多节点部署使用）
 * <p>
 * 授权码 / 隐式模式中，未登录用户访问 {@code /oauth/authorize} 时，Spring Security 会把该请求
 * 存起来，等登录成功后再跳回去。默认实现 {@code HttpSessionRequestCache} 把请求放在 HTTP 会话里 ——
 * 多节点部署时，若这一步与随后的登录请求被负载均衡分到不同节点，就会「登录成功却没跳回授权页」。
 * <p>
 * 本实现把「跳回地址」放进 Redis，并以<b>一个独立 Cookie</b> 作为索引：
 * <ul>
 *   <li>存 Redis 而非会话 → 任一节点都能读到，不依赖会话亲和；</li>
 *   <li>用独立 Cookie 而非 sessionId → 即使会话在授权与登录之间被重建，索引依然有效；</li>
 *   <li>只存「相对地址」（路径 + 查询串）→ 天然与源无关，经反向代理或直连后端都能正确跳回。</li>
 * </ul>
 * <b>取舍</b>：只保留跳转地址，不保留原始请求的方法、参数、请求头。
 * 本流程不需要「重放原请求」——登录后由成功处理器按缓存地址跳转即可，
 * 因此 {@link #getMatchingRequest} 直接返回 {@code null}（交给默认实现去包装请求反而会丢掉登录表单）。
 * <p>
 * 注意这不是会话共享的替代品：登录态本身仍存在 HTTP 会话中，需由 Spring Session 或粘性会话保证跨节点可见。
 */
public class RedisRequestCache implements RequestCache {

    private static final String KEY_PREFIX = "oauth2:reqcache:";
    private static final String COOKIE_NAME = "RBAC_OAUTH2_REQ";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();

    public RedisRequestCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        String cacheId = readCacheId(request);
        if (cacheId == null) {
            cacheId = newCacheId();
            response.addCookie(buildCookie(cacheId));
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + cacheId, currentUrl(request), TTL);
    }

    @Override
    public SavedRequest getRequest(HttpServletRequest request, HttpServletResponse response) {
        String cacheId = readCacheId(request);
        if (cacheId == null) {
            return null;
        }
        String url = stringRedisTemplate.opsForValue().get(KEY_PREFIX + cacheId);
        return StringUtils.hasText(url) ? new SimpleSavedRequest(url) : null;
    }

    /**
     * 恒返回 null：本流程不重放原请求，登录后由成功处理器按 {@link #getRequest} 的结果跳转
     */
    @Override
    public HttpServletRequest getMatchingRequest(HttpServletRequest request, HttpServletResponse response) {
        return null;
    }

    @Override
    public void removeRequest(HttpServletRequest request, HttpServletResponse response) {
        String cacheId = readCacheId(request);
        if (cacheId != null) {
            stringRedisTemplate.delete(KEY_PREFIX + cacheId);
        }
    }

    /** 相对地址（路径 + 查询串），与源无关 */
    private static String currentUrl(HttpServletRequest request) {
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? request.getRequestURI() + "?" + query : request.getRequestURI();
    }

    private static String readCacheId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String newCacheId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Cookie buildCookie(String cacheId) {
        Cookie cookie = new Cookie(COOKIE_NAME, cacheId);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) TTL.getSeconds());
        return cookie;
    }
}
