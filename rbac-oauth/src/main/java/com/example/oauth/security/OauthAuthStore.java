package com.example.oauth.security;

import com.example.oauth.entity.SysUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 授权流程的登录态（Redis）
 * <p>
 * 授权码 / 隐式模式要求用户先在授权服务上认证一次。这里<b>不把登录态交给 HTTP 会话</b>
 * （那会被 Spring Session 序列化成一大团 Java 对象存进 Redis），而是用一张精简的表：
 *
 * <pre>
 *   oauth2:auth:{token}  →  {"userId":1,"username":"admin","nickname":"超级管理员","loginTime":"..."}
 *   浏览器 Cookie: RBAC_OAUTH2_AUTH = token（HttpOnly，Path=/，30 分钟）
 * </pre>
 *
 * 只存「认证信息」——身份是谁、什么时候登录的。权限、令牌、授权请求都不在这里：
 * <ul>
 *   <li>权限由 rbac-backend 的快照与子系统自己的同步表负责，授权服务本来就不判权；</li>
 *   <li>待授权的请求由 {@link RedisRequestCache} 用另一个 Cookie 单独存（它是流程状态，不是身份）；</li>
 *   <li>令牌由 {@code SsoTokenServices} 绑定到 SSO 会话（{@code sso:session:*}）。</li>
 * </ul>
 * 这样 Redis 里存的是人能看懂的 JSON，踢人/排查时一条 {@code GET oauth2:auth:{token}} 就知道是谁。
 *
 * <h3>为什么用独立 Cookie 而不是 JSESSIONID</h3>
 * 与会话解耦：会话（Spring MVC 为「待授权请求」建的）在授权与登录之间被重建也不影响登录态；
 * 多节点部署时任取一台都能用同一个 token 在 Redis 里读到同一份认证信息。
 * <p>
 * <b>键名是跨服务契约</b>：rbac-backend 的「强制下线」会按 {@code sso:browser:{username}}
 * 取出这个 token 并删掉 {@code oauth2:auth:{token}}，两边改动需同步。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OauthAuthStore {

    private static final String KEY_PREFIX = "oauth2:auth:";

    /** 浏览器侧的登录态 Cookie */
    public static final String COOKIE_NAME = "RBAC_OAUTH2_AUTH";

    /** 与原来的会话超时一致 */
    private static final long TTL_SECONDS = 30 * 60;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 登录成功后写入，返回浏览器要保存的 token
     */
    public String create(LoginUser loginUser) {
        String token = UUID.randomUUID().toString().replace("-", "");
        SysUser user = loginUser.getUser();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("loginTime", LocalDateTime.now().format(TIME_FORMAT));
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + token, objectMapper.writeValueAsString(info),
                            TTL_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            throw new IllegalStateException("写入登录态失败", e);
        }
        return token;
    }

    /**
     * 按 token 还原登录用户；不存在或过期返回 null（调用方据此当作未登录）
     * <p>
     * 每次命中都续期 —— 与 HTTP 会话的滑动过期一致，用户在授权页停留不会突然失效。
     */
    public LoginUser read(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key = KEY_PREFIX + token;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        stringRedisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
        try {
            JsonNode node = objectMapper.readTree(json);
            SysUser user = new SysUser();
            if (node.hasNonNull("userId")) {
                user.setId(node.get("userId").asLong());
            }
            user.setUsername(text(node, "username"));
            user.setNickname(text(node, "nickname"));
            // 能读到这条记录就说明登录是有效的；停用/离职会由 rbac-backend 删掉它
            user.setStatus(1);
            return new LoginUser(user, token, Collections.emptySet(), Collections.emptySet());
        }
        catch (Exception e) {
            log.warn("登录态反序列化失败，按未登录处理: {}", e.getMessage());
            return null;
        }
    }

    public void delete(String token) {
        if (StringUtils.hasText(token)) {
            stringRedisTemplate.delete(KEY_PREFIX + token);
        }
    }

    /** 从请求 Cookie 里取 token */
    public static String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return StringUtils.hasText(cookie.getValue()) ? cookie.getValue() : null;
            }
        }
        return null;
    }

    public static void writeCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) TTL_SECONDS);
        response.addCookie(cookie);
    }

    /** 登出时让浏览器丢掉它（Max-Age=0） */
    public static void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
