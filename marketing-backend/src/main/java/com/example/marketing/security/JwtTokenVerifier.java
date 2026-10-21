package com.example.marketing.security;

import com.example.marketing.config.OAuth2ClientProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * 本地校验授权服务签发的访问令牌
 * <p>
 * 令牌是 RS256 JWT，签名公钥由授权服务的 {@code GET /oauth/token_key} 公开提供。
 * 公钥启动时拉取一次，之后<b>定时刷新</b>，验签失败时也会按需重拉（带节流），
 * 因此授权服务轮换密钥后本服务无需重启；除这两条路径外不再回调授权服务，
 * 授权服务不可用时业务接口也能正常工作。
 * <p>
 * 本地验签无法得知「会话是否已被登出/踢人」，因此额外查一次 {@link TokenRevocationStore}：
 * 授权服务销毁会话时会把作废的 sid 广播过来。撤权的最坏延迟 = 令牌剩余有效期（最长 30 分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenVerifier {

    private static final String TOKEN_KEY_PATH = "/oauth/token_key";

    /**
     * 验签失败后允许重新拉取公钥的最小间隔。
     * <p>
     * 既能在密钥轮换后尽快拿到新公钥，又不会让伪造/损坏的令牌把授权服务打成一分钟成千上万次公钥查询。
     */
    private static final long MIN_PUBLIC_KEY_FETCH_INTERVAL_MS = 5_000L;

    private final RestTemplate restTemplate;
    private final OAuth2ClientProperties properties;
    private final TokenRevocationStore tokenRevocationStore;

    private volatile PublicKey publicKey;

    /** 最近一次拉取公钥的时间（毫秒），全部在 synchronized 方法内读写 */
    private long lastFetchAt;

    /**
     * 启动时拉取公钥。失败不阻塞启动：授权服务可能比本系统晚起，
     * 此时保留为空，第一次请求会再尝试一次（见 {@link #verify(String)}）。
     */
    @PostConstruct
    void init() {
        refreshPublicKey();
    }

    /** 令牌校验结论 */
    public enum TokenStatus {
        /** 有效，{@link VerifyResult#getUsername()} 可用 */
        VALID,
        /** 签名有效但已过期：BFF 可以拿 refresh token 静默续期 */
        EXPIRED,
        /** 签名不匹配、格式非法、跨系统重放、会话已撤销等，只能拒绝 */
        INVALID
    }

    public static final class VerifyResult {

        private static final VerifyResult EXPIRED = new VerifyResult(TokenStatus.EXPIRED, null);
        private static final VerifyResult INVALID = new VerifyResult(TokenStatus.INVALID, null);

        private final TokenStatus status;
        private final String username;

        private VerifyResult(TokenStatus status, String username) {
            this.status = status;
            this.username = username;
        }

        static VerifyResult valid(String username) {
            return new VerifyResult(TokenStatus.VALID, username);
        }

        static VerifyResult expired() {
            return EXPIRED;
        }

        static VerifyResult invalid() {
            return INVALID;
        }

        public boolean isValid() {
            return status == TokenStatus.VALID;
        }

        public boolean isExpired() {
            return status == TokenStatus.EXPIRED;
        }

        public String getUsername() {
            return username;
        }
    }

    /**
     * 令牌 → 校验结论 + 用户名。
     * <p>
     * 与只返回 null 的旧实现相比，这里把「过期」单独分出来：BFF 需要据此决定是用 refresh token
     * 静默续期，还是直接判定会话不可用。
     */
    public VerifyResult verify(String token) {
        PublicKey key = publicKey;
        if (key == null) {
            key = refreshPublicKey();
        }
        if (key == null) {
            log.warn("尚未取得授权服务公钥，无法校验令牌: serverUrl={}", properties.getServerUrl());
            return VerifyResult.invalid();
        }
        try {
            return toResult(verifyClaims(token, key));
        } catch (ExpiredJwtException e) {
            log.debug("令牌已过期: {}", e.getMessage());
            return VerifyResult.expired();
        } catch (Exception e) {
            // 可能授权服务刚轮换了密钥：重新拉一次公钥再试（按需刷新有节流，坏令牌不会打爆授权服务）
            PublicKey refreshed = refreshPublicKeyIfStale();
            if (refreshed != null && refreshed != key) {
                try {
                    return toResult(verifyClaims(token, refreshed));
                } catch (ExpiredJwtException expired) {
                    log.debug("令牌已过期: {}", expired.getMessage());
                    return VerifyResult.expired();
                } catch (Exception retryError) {
                    log.debug("令牌校验失败: {}", retryError.getMessage());
                    return VerifyResult.invalid();
                }
            }
            // 签名不匹配、格式非法、alg 被篡改等，一律视为无效令牌
            log.debug("令牌校验失败: {}", e.getMessage());
            return VerifyResult.invalid();
        }
    }

    private VerifyResult toResult(String username) {
        return username == null ? VerifyResult.invalid() : VerifyResult.valid(username);
    }

    /** 验签并读取身份；异常由 {@link #verify(String)} 统一处理 */
    private String verifyClaims(String token, PublicKey key) {
        // jjwt 会按令牌头里的 alg 验签，并自动校验 exp
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        // 三个子系统共用同一把签名公钥：不校验 client_id 的话，签发给 marketing 的令牌
        // 可以原样拿到 order/audit 使用。令牌是给哪个客户端签的，就只允许哪个客户端用。
        String expectedClientId = properties.getClientId();
        Object actualClientId = claims.get("client_id");
        if (!StringUtils.hasText(expectedClientId)
                || !expectedClientId.equals(String.valueOf(actualClientId))) {
            log.warn("令牌的 client_id 与本系统不一致，拒绝跨系统重放: expected={}, actual={}",
                    expectedClientId, actualClientId);
            return null;
        }
        Object sid = claims.get("sid");
        if (sid != null && tokenRevocationStore.isRevoked(sid.toString())) {
            log.info("令牌所属会话已撤销，拒绝访问: sid={}", sid);
            return null;
        }
        Object username = claims.get("user_name");
        if (username == null) {
            log.warn("令牌声明里没有 user_name，无法识别身份: keys={}", claims.keySet());
            return null;
        }
        return username.toString();
    }

    /** 拉取公钥（启动、定时任务与按需刷新共用）：成功则替换，失败保留旧值 */
    public synchronized PublicKey refreshPublicKey() {
        return doRefreshPublicKey();
    }

    /**
     * 验签失败时的按需刷新。距上次拉取不足 {@link #MIN_PUBLIC_KEY_FETCH_INTERVAL_MS} 时直接返回当前公钥，
     * 密钥没有真正轮换时对象身份不变，调用方据此决定是否重试验签。
     */
    private synchronized PublicKey refreshPublicKeyIfStale() {
        if (System.currentTimeMillis() - lastFetchAt < MIN_PUBLIC_KEY_FETCH_INTERVAL_MS) {
            return publicKey;
        }
        return doRefreshPublicKey();
    }

    private PublicKey doRefreshPublicKey() {
        lastFetchAt = System.currentTimeMillis();
        String url = properties.getServerUrl() + TOKEN_KEY_PATH;
        try {
            Map<?, ?> body = restTemplate.getForObject(url, Map.class);
            Object value = body == null ? null : body.get("value");
            if (value == null || !StringUtils.hasText(value.toString())) {
                log.warn("授权服务未返回公钥内容: {}", url);
                return publicKey;
            }
            // 标准 PEM：去掉首尾标记与所有空白后按 Base64 解码，得到 X.509 的 DER
            String base64 = value.toString()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PublicKey parsed = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            // 密钥没变时保留原对象：调用方用对象身份判断是否发生了轮换
            if (publicKey != null && Arrays.equals(publicKey.getEncoded(), parsed.getEncoded())) {
                log.debug("授权服务公钥未变化: {}", url);
                return publicKey;
            }
            publicKey = parsed;
            log.info("已加载授权服务令牌公钥: {}", url);
            return publicKey;
        } catch (Exception e) {
            log.warn("拉取授权服务公钥失败，继续使用当前公钥: url={}, error={}", url, e.getMessage());
            return publicKey;
        }
    }
}
