package com.example.rbac.security;

import com.example.rbac.dto.SsoSessionView;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SSO 会话的读取与清除（Redis）
 * <p>
 * 会话由 rbac-oauth 在令牌签发时写入（{@code SsoTokenServices}），本服务只读列表、
 * 以及在「让某用户下线」时把它删掉。删掉即等于该用户在所有子系统的令牌<b>下一个请求就失效</b> ——
 * 因为子系统的每个请求都会拿令牌去 {@code /oauth/check_token}，而那边会要求这个键存在。
 * <p>
 * <b>键名是与 rbac-oauth 的契约</b>，改动需两边同步（见那边的 {@code SsoSessionStore}）：
 * <ul>
 *   <li>{@code sso:session:{username}} —— 会话 JSON，存在=令牌可用</li>
 *   <li>{@code sso:browser:{username}} —— 该用户浏览器登录态的 token（登录时由授权服务记录）</li>
 *   <li>{@code sso:uid:{userId}} —— userId → username 反查索引，让本服务能按 userId 操作</li>
 *   <li>{@code oauth2:auth:{token}} —— 授权服务侧的认证信息（JSON），由上面的 token 定位</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionStore {

    private static final String SESSION_KEY = "sso:session:";
    private static final String BROWSER_KEY = "sso:browser:";
    private static final String USER_ID_KEY = "sso:uid:";

    /**
     * 授权服务侧的登录态键前缀（见 rbac-oauth 的 {@code OauthAuthStore}）。
     * <p>
     * <b>键名是与 rbac-oauth 的契约</b>：那边登录成功时写入 {@code oauth2:auth:{token}}，
     * 并把 token 记进 {@code sso:browser:{username}}。踢人时本服务按用户名取出 token、删掉这条记录 ——
     * 光删令牌不删登录态，浏览器还能拿旧 Cookie 静默重走授权码换出新令牌、把会话重建出来。
     */
    private static final String OAUTH_AUTH_PREFIX = "oauth2:auth:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 全部在线会话，供管理页列表使用。用 SCAN 而非 KEYS，避免阻塞 Redis 单线程。
     */
    public List<SsoSessionView> listAll() {
        List<SsoSessionView> sessions = new ArrayList<>();
        for (String key : scanKeys(SESSION_KEY + "*")) {
            SsoSessionView view = parse(stringRedisTemplate.opsForValue().get(key));
            if (view != null) {
                sessions.add(view);
            }
        }
        return sessions;
    }

    public String usernameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(USER_ID_KEY + userId);
    }

    /**
     * 读某个用户的会话（门户据此展示「已登录过哪些子系统」）。不存在返回 null。
     */
    public SsoSessionView read(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return parse(stringRedisTemplate.opsForValue().get(SESSION_KEY + username));
    }

    /**
     * 让某用户在所有子系统下线（按 userId，与 rbac-backend 其余接口的入参习惯一致）。
     * <p>
     * 通过 {@code sso:uid:{userId}} 索引换成 username 再删；索引不存在说明该用户本来就没有 SSO 会话
     * （从没登录过门户/子系统），直接返回 false，不是错误。
     */
    public boolean removeByUserId(Long userId) {
        return removeByUsername(usernameOf(userId));
    }

    /**
     * 删除会话、浏览器登录态索引，并把授权服务那边的认证信息一并删掉。
     * <p>
     * 三步缺一不可：删会话让令牌失效，删登录态让「静默重登录」也走不通，
     * 删索引避免下次按 userId 查到已经不存在的人。
     */
    public boolean removeByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String sessionJson = stringRedisTemplate.opsForValue().get(SESSION_KEY + username);
        String authToken = stringRedisTemplate.opsForValue().get(BROWSER_KEY + username);

        stringRedisTemplate.delete(SESSION_KEY + username);
        stringRedisTemplate.delete(BROWSER_KEY + username);

        SsoSessionView session = parse(sessionJson);
        if (session != null && session.getUserId() != null) {
            stringRedisTemplate.delete(USER_ID_KEY + session.getUserId());
        }
        deleteOauthAuth(username, authToken);

        return sessionJson != null;
    }

    /**
     * 删掉授权服务侧那条登录态（{@code oauth2:auth:{token}}）。
     * <p>
     * 直接删 Redis 键而不是走 HTTP 调用：两个服务共享同一个 Redis（database 0），
     * 而这条记录的结构很简单（一个 JSON 值）。删掉后浏览器再带着旧 Cookie 请求
     * {@code /oauth/authorize} 就查不到身份，等价于要求重新登录。
     */
    private void deleteOauthAuth(String username, String authToken) {
        if (!StringUtils.hasText(authToken)) {
            return;
        }
        stringRedisTemplate.delete(OAUTH_AUTH_PREFIX + authToken);
        log.info("已删除授权服务侧的登录态: username={}", username);
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

    private SsoSessionView parse(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            SsoSessionView view = new SsoSessionView();
            view.setUsername(text(node, "username"));
            view.setUserId(node.hasNonNull("userId") ? node.get("userId").asLong() : null);
            view.setNickname(text(node, "nickname"));
            view.setLoginTime(text(node, "loginTime"));
            if (node.has("clients") && node.get("clients").isArray()) {
                List<String> clients = new ArrayList<>();
                node.get("clients").forEach(c -> clients.add(c.asText()));
                view.setClients(clients);
            }
            return view;
        }
        catch (Exception e) {
            log.warn("SSO 会话反序列化失败，跳过该条: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
