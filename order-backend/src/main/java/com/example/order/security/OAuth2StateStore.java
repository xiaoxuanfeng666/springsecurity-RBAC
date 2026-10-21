package com.example.order.security;

import com.example.order.config.OAuth2ClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 授权请求 state 的存储（Redis）+ 浏览器绑定（HttpOnly Cookie）
 * <p>
 * state 是授权码流程的 CSRF / 重放防护：
 * <ul>
 *   <li>{@code /auth/sso-login} 生成随机 state，写入 Redis，并同时下发一个 HttpOnly Cookie；</li>
 *   <li>回调页从地址栏取出 state 随 {@code /auth/code} 提交；</li>
 *   <li>服务端要求 <b>请求体 state == Cookie state</b>，再用 Lua 原子消费 Redis 里那条记录。</li>
 * </ul>
 * Cookie 绑定是防「登录 CSRF」的关键：攻击者可以把自己拿到的 code + state 塞进受害者浏览器，
 * 但受害者浏览器里没有对应的 Cookie（或已有的是另一个 state），校验会失败。
 * <p>
 * 键名与 Cookie 名都带 client_id，避免多个系统共用同一个 Redis / 同一个 localhost 时互相串用。
 */
@Component
@RequiredArgsConstructor
public class OAuth2StateStore {

    private static final String KEY_PREFIX = "oauth2:state:";
    private static final String COOKIE_PREFIX = "RBAC_OAUTH_STATE_";

    /** 与授权中心的请求缓存（30 分钟）对齐：用户在登录页等待过久需重新发起授权 */
    private static final long TTL_SECONDS = 30 * 60;

    /** 存在才删除；返回 1 表示这次消费有效，0 表示过期或已被使用 */
    private static final RedisScript<Long> TAKE_STATE = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then redis.call('del', KEYS[1]) return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final OAuth2ClientProperties properties;
    private final SecureRandom random = new SecureRandom();

    /** 签发 state：写 Redis + 写浏览器 Cookie，返回给授权地址使用 */
    public String issue(HttpServletResponse response) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stringRedisTemplate.opsForValue().set(key(state), "1", TTL_SECONDS, TimeUnit.SECONDS);
        addCookie(response, state, (int) TTL_SECONDS);
        return state;
    }

    /**
     * 消费 state：Cookie 必须与请求体一致，Redis 里的记录必须存在且未被使用。
     * 成功后立即删除 Cookie；校验失败不清理 Cookie，避免别人构造的回调把本机正在进行的登录打断。
     */
    public boolean consume(String state, HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.hasText(state)) {
            return false;
        }
        String cookieState = readCookie(request);
        if (!state.equals(cookieState)) {
            return false;
        }
        Long result = stringRedisTemplate.execute(TAKE_STATE, Collections.singletonList(key(state)));
        addCookie(response, "", 0);
        return result != null && result == 1L;
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void addCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        // Servlet 4.0 的 Cookie 不支持 SameSite 属性，直接拼 Set-Cookie 头。
        // 生产环境走 HTTPS 时应在末尾追加 "; Secure"（开发环境是 http，加了会导致浏览器丢弃 Cookie）
        String header = cookieName() + "=" + value
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds;
        response.addHeader("Set-Cookie", header);
    }

    private String cookieName() {
        return COOKIE_PREFIX + properties.getClientId().toUpperCase().replace('-', '_');
    }

    private String key(String state) {
        return KEY_PREFIX + properties.getClientId() + ":" + state;
    }
}
