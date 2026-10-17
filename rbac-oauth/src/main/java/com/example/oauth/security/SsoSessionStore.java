package com.example.oauth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 统一门户 SSO 会话（Redis）
 * <p>
 * 这是「企业级统一门户」的核心：授权服务签发的令牌是<b>自包含 JWT</b>，签发出去就收不回来，
 * 天然没有「登出」的概念。为此在令牌之外再加一层会话，把令牌绑到会话上：
 * <ul>
 *   <li>令牌签发 / 刷新时建立或续期会话；</li>
 *   <li>{@code /oauth/check_token} 校验令牌时<b>额外要求会话存在</b>（见 {@code SsoTokenServices}）；</li>
 *   <li>统一登出（或后台踢人）删掉会话，该用户所有客户端的令牌<b>下一个请求即失效</b>。</li>
 * </ul>
 * 这套「删会话即下线」的做法沿用了 rbac-backend 管理端登录态（{@code login:session:*}）的既有模式，
 * 只是把作用域从「管理端一个系统」扩大到「所有子系统」。
 *
 * <h3>为什么会话键用 username 而不是 userId</h3>
 * 令牌里唯一权威的身份就是 {@code user_name} 声明 —— JWT 是自包含的，服务端手里只有令牌本身，
 * 每次校验都拿不到（也不该去查库拿）userId。而 {@code user_name} 在访问令牌和刷新令牌里
 * <b>始终存在</b>（由 {@code DefaultAccessTokenConverter} 无条件写入），所以：
 * <ul>
 *   <li>校验路径可以直接从令牌声明取键，<b>一次 Redis GET，无数据库查询</b>；</li>
 *   <li>刷新路径也能从刷新令牌的认证对象拿到同名值，不必再按 user_name 反查 sys_user 补 userId；</li>
 *   <li>子系统本来就按 {@code user_name} 认人（{@code selectByUsername}），会话键与之一致，不会出现
 *       「会话按 userId、业务按 username」两套身份对不上的情况。</li>
 * </ul>
 * userId 仍然记在会话内容里，仅供管理端展示与「按 userId 踢人」时比对。
 *
 * <h3>Redis 键契约（rbac-backend 会读同一批键，两边改动需同步）</h3>
 * <ul>
 *   <li>{@code sso:session:{username}} —— SSO 会话 JSON，<b>权威开关</b>：存在=令牌可用。TTL 7 天</li>
 *   <li>{@code sso:browser:{username}} —— 该用户浏览器会话的 Spring Session id。
 *       踢人时没有浏览器 cookie 可用，靠这条反查索引才能把他的浏览器会话一并销毁。TTL 7 天</li>
 *   <li>{@code sso:uid:{userId}} —— userId → username 的反查索引。
 *       rbac-backend 的接口一律以 userId 为参数（与 /auth/kick/{userId} 等既有接口一致），
 *       而会话键用的是 username，中间需要一步转换；有这个索引就不必为每次「踢人」去查一次库。</li>
 * </ul>
 *
 * <h3>为什么 TTL 是 7 天</h3>
 * 与客户端 {@code refresh_token_validity}（604800 秒）对齐：刷新令牌活着的时候会话就该活着，
 * 这样「不用就自然过期」的语义由刷新令牌的期限统一决定，不必在这里另立一套。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionStore {

    private static final String SESSION_KEY = "sso:session:";
    private static final String BROWSER_KEY = "sso:browser:";
    private static final String USER_ID_KEY = "sso:uid:";

    /**
     * 与客户端 refresh_token_validity 对齐
     */
    private static final long TTL_SECONDS = 7 * 24 * 60 * 60L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final OauthAuthStore oauthAuthStore;

    /**
     * 建立会话；已存在则续期，并把本次的客户端追加进去。
     * <p>
     * 只在令牌签发路径（密码 / 授权码 / 隐式模式）调用 —— 客户端凭证模式没有用户，不建会话。
     */
    public void createOrRenew(String username, Long userId, String nickname, String clientId) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        SsoSession session = read(username);
        if (session == null) {
            session = new SsoSession(username, userId, nickname,
                    LocalDateTime.now().format(TIME_FORMAT), new ArrayList<>());
        } else {
            // 续期时刷新展示用的信息：用户改了昵称后，后台列表不该还显示旧的
            session.setUserId(userId);
            session.setNickname(nickname);
        }
        session.addClient(clientId);
        write(session);
    }

    /**
     * 会话是否存在。check_token 每次调用都会走这里，因此用 {@code hasKey} 而不反序列化 JSON。
     */
    public boolean exists(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(SESSION_KEY + username));
    }

    /**
     * 续期（仅在键已存在时延长 TTL，绝不新建）。
     * <p>
     * 刷新令牌换新令牌后调用，让活跃用户的会话继续存活。
     * 调 {@code expire} 而不是 {@code createOrRenew} 是刻意的 —— 后者会把已经登出的会话重新建出来，
     * 那样「登出后再刷新」就能把 SSO 会话复活，统一登出会被绕过。
     */
    public void touch(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        stringRedisTemplate.expire(SESSION_KEY + username, TTL_SECONDS, TimeUnit.SECONDS);
    }

    public void delete(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        SsoSession session = read(username);
        String token = stringRedisTemplate.opsForValue().get(BROWSER_KEY + username);
        if (StringUtils.hasText(token)) {
            //由于oauth2:auth: key是覆盖操作
            oauthAuthStore.delete(token);
        }
        stringRedisTemplate.delete(SESSION_KEY + username);
        stringRedisTemplate.delete(BROWSER_KEY + username);
        if (session != null && session.getUserId() != null) {
            stringRedisTemplate.delete(USER_ID_KEY + session.getUserId());
        }
    }

    /**
     * userId → username 的反查（读 {@code sso:uid:{userId}} 索引）。
     * <p>
     * 供 rbac-backend 按 userId 删会话用；本服务自己用不到（本服务的入口都自带 username）。
     */
    public String usernameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(USER_ID_KEY + userId);
    }

    public SsoSession read(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return parse(stringRedisTemplate.opsForValue().get(SESSION_KEY + username));
    }

    /**
     * 全部在线会话，供管理端「SSO 会话」列表使用。
     * <p>
     * 用 SCAN 游标而非 {@code KEYS}：后者会阻塞 Redis 单线程，而这张列表是页面上随时可能被刷新的。
     * 会话数量本来就不大，但没理由给一个会拖慢整个 Redis 的实现在生产里留口子。
     */
    public List<SsoSession> listAll() {
        List<SsoSession> sessions = new ArrayList<>();
        for (String key : scanKeys(SESSION_KEY + "*")) {
            SsoSession session = parse(stringRedisTemplate.opsForValue().get(key));
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new LinkedHashSet<>();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }

    /**
     * 记下该用户浏览器登录态的 token（登录成功时写入）。
     * <p>
     * 存在的意义只有一个：后台踢人时手上没有对方的 Cookie，只有身份标识。
     * 靠这条索引才能定位到 {@code oauth2:auth:{token}} 并把它删掉 ——
     * 只删令牌会话不删登录态的话，浏览器能拿旧 Cookie 静默重走一遍授权码，换出新令牌、重建会话。
     */
    public void bindBrowserSession(String username, String token) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(token)) {
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(BROWSER_KEY + username, token, TTL_SECONDS, TimeUnit.SECONDS);
    }

    public String getBrowserSessionId(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(BROWSER_KEY + username);
    }

    private SsoSession parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SsoSession.class);
        } catch (Exception e) {
            // 值格式不对（历史数据、被别处写坏）时按「会话不存在」处理：
            // 宁可让用户重新登录，也不能因为一条脏数据让校验逻辑抛异常。
            log.warn("SSO 会话反序列化失败，按不存在处理: {}", e.getMessage());
            return null;
        }
    }

    private void write(SsoSession session) {
        try {
            stringRedisTemplate.opsForValue().set(SESSION_KEY + session.getUsername(),
                    objectMapper.writeValueAsString(session), TTL_SECONDS, TimeUnit.SECONDS);
            if (session.getUserId() != null) {
                stringRedisTemplate.opsForValue()
                        .set(USER_ID_KEY + session.getUserId(), session.getUsername(),
                                TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入 SSO 会话失败: username=" + session.getUsername(), e);
        }
    }
}
