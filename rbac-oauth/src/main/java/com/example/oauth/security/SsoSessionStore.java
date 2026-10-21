package com.example.oauth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
 *   <li>{@code sso:refresh-used:{sha256(token)}} —— 刷新令牌「已用」墓碑，用于识别令牌复用。
 *       TTL 覆盖刷新令牌整个有效期。</li>
 * </ul>
 *
 * <h3>会话有效期：空闲超时 + 绝对上限</h3>
 * 会话是<b>用户级</b>的（一个用户一份，多客户端共享），因此它的寿命是会话策略，
 * 不跟随某个客户端的 {@code refresh_token_validity} —— 客户端令牌是会话的子集。
 * <ul>
 *   <li>{@code idle}：每次刷新令牌时续期；不用就自然过期；</li>
 *   <li>{@code max}：从登录时刻算起，永不延长，防止「一直用就永不重登」。</li>
 * </ul>
 * Redis 里实际写入的 TTL 取两者较小值，见 {@code rbac.sso.session-*-ttl-seconds}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionStore {

    private static final String SESSION_KEY = "sso:session:";
    private static final String BROWSER_KEY = "sso:browser:";
    private static final String USER_ID_KEY = "sso:uid:";
    /** 刷新令牌 → 签发时的 sid，刷新时用它确认仍是同一个浏览器会话 */
    private static final String REFRESH_SID_KEY = "sso:refresh-sid:";

    /**
     * 用户 → 其名下活跃刷新令牌哈希的集合，用于登出/会话轮换时精准清理上面那批键。
     * <p>
     * 刷新令牌本身不直接放进 Redis：键里存的是它的 SHA-256，拿到 Redis 数据也无法反推出可用令牌。
     */
    private static final String REFRESH_INDEX_KEY = "sso:refresh-sid:user:";

    /**
     * 已消费过的刷新令牌墓碑：{@code sso:refresh-used:{sha256(token)} = sid}。
     * <p>
     * 只有绑定键被删掉之后才写，用来区分两种「取不到 sid」的情形：
     * <ul>
     *   <li>绑定已删但墓碑在 → 这张令牌<b>用过</b>，再次提交即「复用」，视为令牌泄露；</li>
     *   <li>两者都没有 → 本服务从未签发过它，普通拒绝即可，不能据此撤销毁正常会话。</li>
     * </ul>
     * 值的 sid 用于判断被复用的令牌属于哪个会话：只有它仍对应当前活跃会话时才撤权，
     * 否则（用户早已重新登录、换了新会话）静默拒绝，避免旧令牌重放误伤新登录的会话。
     */
    private static final String REFRESH_USED_KEY = "sso:refresh-used:";

    /**
     * 原子地消费刷新令牌：删绑定、写墓碑，返回三态结果。
     * <p>
     * 刷新令牌是一次性的：轮换后旧令牌的绑定必须消失，再用就取不到 sid、直接拒绝。
     * 用脚本而不是先 GET 再 DEL / SET，是为了让同一张刷新令牌的并发请求只有一个能拿到值 ——
     * 否则两个请求都读到 sid，会各自换出一组新令牌，「一次使用」的语义就被打破了。
     * <p>
     * 返回：{@code 1:{sid}} 首次使用；{@code 2:{sid}} 复用（墓碑里记录的 sid）；
     * {@code 0} 从未签发（或墓碑已过期）。
     */
    private static final RedisScript<String> CONSUME_REFRESH_SID = new DefaultRedisScript<>(
            "local sid = redis.call('get', KEYS[1]) "
                    + "if sid then "
                    + "  redis.call('del', KEYS[1]) "
                    + "  redis.call('set', KEYS[2], sid, 'EX', ARGV[1]) "
                    + "  return '1:' .. sid "
                    + "end "
                    + "local used = redis.call('get', KEYS[2]) "
                    + "if used then return '2:' .. used end "
                    + "return '0'",
            String.class);

    /** 空闲超时（秒）：每次刷新续期 */
    @Value("${rbac.sso.session-idle-ttl-seconds:604800}")
    private long idleTtlSeconds;

    /** 绝对上限（秒）：从登录时刻算起，永不延长 */
    @Value("${rbac.sso.session-max-ttl-seconds:2592000}")
    private long maxTtlSeconds;

    /**
     * 撤销记录的兜底保留时长（秒）。
     * <p>
     * 正常情况下由会话所涉客户端的 {@code access_token_validity} 取最大值决定；
     * 只有客户端已被删除/配置读不到时才用这个值。默认为 Spring 的默认访问令牌有效期（12 小时）。
     */
    @Value("${rbac.session.revoke-default-ttl-seconds:43200}")
    private long revokeDefaultTtlSeconds;

    /**
     * 刷新令牌墓碑（已用记录）的保留时长（秒）。
     * <p>
     * 必须不小于刷新令牌自身的有效期，否则令牌还没过期、墓碑先没了，复用就会被误判成「从未签发」。
     * 默认 7 天，与 clientdetails 里各客户端的 {@code refresh_token_validity} 对齐。
     */
    @Value("${rbac.sso.refresh-reuse-ttl-seconds:604800}")
    private long refreshReuseTtlSeconds;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final OauthAuthStore oauthAuthStore;
    private final com.example.oauth.mq.SessionRevokePublisher sessionRevokePublisher;
    private final ClientDetailsService clientDetailsService;

    /**
     * 建立会话；已存在则续期，并把本次的客户端追加进去。
     * <p>
     * 只在令牌签发路径（密码 / 授权码 / 隐式模式）调用 —— 客户端凭证模式没有用户，不建会话。
     * <p>
     * {@code sessionId} 是当前活跃的浏览器会话标识：与已有会话不一致时视为「新登录」，
     * 轮换会话并删除旧浏览器的登录态，旧浏览器手里的令牌因 sid 不匹配而立即失效。
     */
    public void createOrRenew(String username, Long userId, String nickname,
                              String clientId, String sessionId) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        long now = nowSeconds();
        SsoSession existing = read(username);
        boolean newSession = existing == null
                || !Objects.equals(existing.getSessionId(), sessionId)
                || isAbsolutelyExpired(existing, now);
        SsoSession session;
        if (newSession) {
            if (existing != null && StringUtils.hasText(existing.getSessionId())) {
                // 旧 sid 对浏览器流程就是 oauth2:auth:{token}，删掉后旧浏览器无法静默重新授权
                oauthAuthStore.delete(existing.getSessionId());
                // 子系统本地验签看不到会话轮换，必须显式告知旧 sid 作废
                sessionRevokePublisher.publish(existing.getSessionId(), username, revokeTtlOf(existing));
            }
            // 会话轮换：旧会话签发的刷新令牌一并失效，不必等它们被使用
            clearRefreshBindings(username);
            session = new SsoSession();
            session.setUsername(username);
            session.setSessionId(sessionId);
            session.setLoginTime(LocalDateTime.now().format(TIME_FORMAT));
            session.setClients(new ArrayList<>());
            session.setExpireAt(now + maxTtlSeconds);
        } else {
            session = existing;
            if (session.getExpireAt() == null) {
                // 兼容绝对上限上线前创建的会话：从现在起重新计算
                session.setExpireAt(now + maxTtlSeconds);
            }
        }
        // 续期时刷新展示用的信息：用户改了昵称后，后台列表不该还显示旧的
        session.setUserId(userId);
        session.setNickname(nickname);
        session.addClient(clientId);
        write(session, ttlOf(session, now));
    }

    /** 令牌/刷新令牌的 sid 是否仍是当前活跃会话 */
    public boolean matchesSessionId(String username, String sessionId) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(sessionId)) {
            return false;
        }
        SsoSession session = read(username);
        return session != null && sessionId.equals(session.getSessionId());
    }

    /** 浏览器登录态是否仍然存在（用于拒绝新登录之前拿到的旧授权码） */
    public boolean isBrowserSessionAlive(String sessionId) {
        return StringUtils.hasText(sessionId) && oauthAuthStore.read(sessionId) != null;
    }

    /** 记住刷新令牌签发时的 sid，刷新时据此判断是不是同一个浏览器会话 */
    public void bindRefreshSid(String username, String refreshToken, String sessionId) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(refreshToken)
                || !StringUtils.hasText(sessionId)) {
            return;
        }
        long ttl = effectiveTtl(username);
        if (ttl <= 0) {
            return;
        }
        String hash = hashOf(refreshToken);
        stringRedisTemplate.opsForValue()
                .set(REFRESH_SID_KEY + hash, sessionId, ttl, TimeUnit.SECONDS);
        String indexKey = REFRESH_INDEX_KEY + username;
        stringRedisTemplate.opsForSet().add(indexKey, hash);
        stringRedisTemplate.expire(indexKey, ttl, TimeUnit.SECONDS);
    }

    /**
     * 消费刷新令牌的 sid 绑定。
     * <p>
     * 首次使用返回绑定时的 sid；若这张令牌此前已被消费过（墓碑命中）则标记为复用，
     * 由调用方决定是否撤销整个会话。绑定与墓碑都不存在时按「从未签发」拒绝，不触发撤权。
     */
    public RefreshConsumeResult consumeRefreshSid(String username, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return RefreshConsumeResult.rejected();
        }
        String hash = hashOf(refreshToken);
        String value = stringRedisTemplate.execute(CONSUME_REFRESH_SID,
                Arrays.asList(REFRESH_SID_KEY + hash, REFRESH_USED_KEY + hash),
                String.valueOf(refreshReuseTtlSeconds));
        if (StringUtils.hasText(username)) {
            stringRedisTemplate.opsForSet().remove(REFRESH_INDEX_KEY + username, hash);
        }
        if (!StringUtils.hasText(value)) {
            return RefreshConsumeResult.rejected();
        }
        if (value.startsWith("1:")) {
            return RefreshConsumeResult.firstUse(value.substring(2));
        }
        if (value.startsWith("2:")) {
            return RefreshConsumeResult.reused(value.substring(2));
        }
        return RefreshConsumeResult.rejected();
    }

    /** 刷新令牌消费结果：首次使用 / 复用（含墓碑记录的 sid）/ 从未签发 */
    public static final class RefreshConsumeResult {

        private final boolean reused;
        private final String sid;

        private RefreshConsumeResult(boolean reused, String sid) {
            this.reused = reused;
            this.sid = sid;
        }

        static RefreshConsumeResult firstUse(String sid) {
            return new RefreshConsumeResult(false, sid);
        }

        static RefreshConsumeResult reused(String sid) {
            return new RefreshConsumeResult(true, sid);
        }

        static RefreshConsumeResult rejected() {
            return new RefreshConsumeResult(false, null);
        }

        /** 是否为「已消费过的刷新令牌被再次提交」 */
        public boolean isReused() {
            return reused;
        }

        /** 首次使用时为绑定 sid；复用时为墓碑里记录的 sid；从未签发时为 null */
        public String getSid() {
            return sid;
        }
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
        long now = nowSeconds();
        SsoSession session = read(username);
        if (session == null) {
            return;
        }
        if (isAbsolutelyExpired(session, now)) {
            // 绝对上限已到：直接下线，避免刷新成功后会话却已过期
            delete(username);
            return;
        }
        long ttl = ttlOf(session, now);
        stringRedisTemplate.expire(SESSION_KEY + username, ttl, TimeUnit.SECONDS);
        stringRedisTemplate.expire(BROWSER_KEY + username, ttl, TimeUnit.SECONDS);
        stringRedisTemplate.expire(REFRESH_INDEX_KEY + username, ttl, TimeUnit.SECONDS);
        if (session.getUserId() != null) {
            stringRedisTemplate.expire(USER_ID_KEY + session.getUserId(), ttl, TimeUnit.SECONDS);
        }
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
        // 登出后刷新令牌也必须失效，顺手清掉它们的 sid 绑定，避免残留到会话 TTL 到期
        clearRefreshBindings(username);
        // 子系统本地验签时靠撤销集合感知「会话已销毁」；必须放在删键之后，
        // 这样即使广播失败，本服务自己的校验也已经是拒绝态
        if (session != null && StringUtils.hasText(session.getSessionId())) {
            sessionRevokePublisher.publish(session.getSessionId(), username, revokeTtlOf(session));
        }
    }

    /**
     * 撤销记录要保留多久：取会话涉及客户端的访问令牌有效期最大值。
     * <p>
     * 撤销发生在会话销毁时，此刻签发出的令牌最长还能活 {@code access_token_validity} 秒，
     * 所以撤销记录必须至少活这么久；取各客户端的最大值才能覆盖所有子系统。
     * 客户端读不到（已删除/配置异常）时退回兜底值，宁可多留也不可少留 ——
     * 少留会让已撤销的令牌在记录过期后重新可用。
     */
    private long revokeTtlOf(SsoSession session) {
        long ttl = 0;
        if (session != null && session.getClients() != null) {
            for (String clientId : session.getClients()) {
                try {
                    Integer validity = clientDetailsService.loadClientByClientId(clientId)
                            .getAccessTokenValiditySeconds();
                    // 客户端没配有效期时，框架用默认值（12 小时）
                    long clientTtl = validity == null ? revokeDefaultTtlSeconds : validity;
                    if (clientTtl > ttl) {
                        ttl = clientTtl;
                    }
                } catch (Exception e) {
                    // 读不到某个客户端的配置时按兜底值保留，宁可多留也不可少留
                    log.warn("计算会话撤销 TTL 时加载客户端失败，按兜底值保留: clientId={}, error={}",
                            clientId, e.getMessage());
                    ttl = Math.max(ttl, revokeDefaultTtlSeconds);
                }
            }
        }
        return ttl > 0 ? ttl : revokeDefaultTtlSeconds;
    }

    /** 清掉某用户名下所有刷新令牌的 sid 绑定（登出、会话轮换时调用） */
    private void clearRefreshBindings(String username) {
        String indexKey = REFRESH_INDEX_KEY + username;
        Set<String> hashes = stringRedisTemplate.opsForSet().members(indexKey);
        if (hashes != null) {
            for (String hash : hashes) {
                stringRedisTemplate.delete(REFRESH_SID_KEY + hash);
            }
        }
        stringRedisTemplate.delete(indexKey);
    }

    /** 刷新令牌只以哈希形式出现在 Redis 键里，拿到数据也无法还原出可用令牌 */
    private static String hashOf(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
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
        long ttl = effectiveTtl(username);
        if (ttl <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(BROWSER_KEY + username, token, ttl, TimeUnit.SECONDS);
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

    private void write(SsoSession session, long ttlSeconds) {
        try {
            stringRedisTemplate.opsForValue().set(SESSION_KEY + session.getUsername(),
                    objectMapper.writeValueAsString(session), ttlSeconds, TimeUnit.SECONDS);
            if (session.getUserId() != null) {
                stringRedisTemplate.opsForValue()
                        .set(USER_ID_KEY + session.getUserId(), session.getUsername(),
                                ttlSeconds, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入 SSO 会话失败: username=" + session.getUsername(), e);
        }
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private static boolean isAbsolutelyExpired(SsoSession session, long now) {
        return session != null && session.getExpireAt() != null && session.getExpireAt() <= now;
    }

    /** 实际写入 Redis 的 TTL：空闲超时与「距绝对上限的剩余时间」取小 */
    private long ttlOf(SsoSession session, long now) {
        long remaining = session.getExpireAt() == null
                ? idleTtlSeconds
                : session.getExpireAt() - now;
        return Math.max(1L, Math.min(idleTtlSeconds, remaining));
    }

    /** 该用户当前可用的会话 TTL；会话已不存在或绝对过期返回 0 */
    private long effectiveTtl(String username) {
        long now = nowSeconds();
        SsoSession session = read(username);
        if (session == null) {
            // 浏览器登录态可能先于 SSO 会话建立（登录成功时先写浏览器索引）
            return idleTtlSeconds;
        }
        if (isAbsolutelyExpired(session, now)) {
            return 0;
        }
        return ttlOf(session, now);
    }
}
