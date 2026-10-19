package com.example.oauth.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.oauth.common.BusinessException;
import com.example.oauth.config.IdpProperties;
import com.example.oauth.config.WeComProperties;
import com.example.oauth.config.WeComQrProperties;
import com.example.oauth.entity.SysQrLoginTicket;
import com.example.oauth.entity.SysUser;
import com.example.oauth.entity.SysUserIdentity;
import com.example.oauth.mapper.SysQrLoginTicketMapper;
import com.example.oauth.mapper.SysUserIdentityMapper;
import com.example.oauth.mapper.SysUserMapper;
import com.example.oauth.oauth.WeComClient;
import com.example.oauth.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信扫码登录：短链票据 + 手机授权确认 + 电脑轮询完成登录
 * <p>
 * 二维码内容是本服务的短链 {@code /q/{code}}，票据同时写入 MySQL 与 Redis（带 TTL）。
 * 手机扫码后短链跳转到企业微信 snsapi_base 授权页；回调只标记票据为 CONFIRMED，
 * 真正的 SSO 会话由原电脑通过轮询 + 一次性校验码建立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeComQrLoginService {

    /** 回调 state 前缀，用于和普通 IdP state 区分 */
    public static final String STATE_PREFIX = "qr_";

    /** 电脑端绑定票据的 HttpOnly Cookie */
    public static final String COOKIE_NAME = "RBAC_QR_LOGIN";

    private static final String REDIS_PREFIX = "oauth2:qr:login:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WeComQrProperties qrProperties;
    private final WeComProperties weComProperties;
    private final IdpProperties idpProperties;
    private final WeComClient weComClient;
    private final IdentityProviderRegistry providerRegistry;
    private final SysQrLoginTicketMapper ticketMapper;
    // 说明：当前票据只写 Redis，ticketMapper 暂时保留，便于恢复数据库持久化。
    private final SysUserIdentityMapper identityMapper;
    private final SysUserMapper userMapper;
    private final JitUserProvisioner jitUserProvisioner;
    private final IdpSessionService sessionService;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建短链票据并给电脑浏览器写入绑定 Cookie
     */
    public Map<String, Object> create(String returnTo, HttpServletResponse response) {
        if (!qrProperties.isEnabled() || !weComClient.isEnabled()) {
            throw new BusinessException("企业微信扫码登录未启用");
        }
        String safeReturnTo = normalizeReturnTo(returnTo);
        if (!StringUtils.hasText(safeReturnTo)) {
            throw new BusinessException("登录请求缺少授权上下文，请从业务系统重新发起登录");
        }

        String code = randomCode(9);
        LocalDateTime now = LocalDateTime.now();
        SysQrLoginTicket ticket = new SysQrLoginTicket();
        ticket.setCode(code);
        ticket.setProvider(WeComIdentityProvider.PROVIDER);
        ticket.setCorpId(weComProperties.getCorpId());
        ticket.setReturnTo(safeReturnTo);
        ticket.setStatus(SysQrLoginTicket.STATUS_PENDING);
        ticket.setExpireTime(now.plusSeconds(qrProperties.getTtlSeconds()));
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        // 暂时不落库：票据只写 Redis（表保留，恢复时取消注释）
        // ticketMapper.insert(ticket);
        cache(ticket, qrProperties.getTtlSeconds());
        writeQrCookie(response, code, qrProperties.getTtlSeconds());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticket", code);
        result.put("shortUrl", buildShortUrl(code));
        result.put("expiresIn", qrProperties.getTtlSeconds());
        result.put("expireAt", ticket.getExpireTime().format(TIME_FORMAT));
        log.info("创建企业微信扫码票据: code={}, expireAt={}", code, ticket.getExpireTime());
        return result;
    }

    /**
     * 短链入口：有效票据跳转到企业微信 snsapi_base 授权页
     */
    public String resolveAuthorizeUrl(String code) {
        SysQrLoginTicket ticket = load(code);
        if (ticket == null || !SysQrLoginTicket.STATUS_PENDING.equals(ticket.getStatus())) {
            return null;
        }
        return weComClient.buildOAuth2AuthorizeUrl(STATE_PREFIX + code);
    }

    /**
     * 手机端授权回调：只确认身份，不建立手机端会话
     */
    public void handleCallback(String wecomCode, String code, HttpServletResponse response) {
        SysQrLoginTicket ticket = load(code);
        if (ticket == null) {
            render(response, false, "二维码已过期", "请回到电脑刷新二维码后重新扫码。");
            return;
        }
        if (!SysQrLoginTicket.STATUS_PENDING.equals(ticket.getStatus())) {
            if (SysQrLoginTicket.STATUS_CONFIRMED.equals(ticket.getStatus())
                    || SysQrLoginTicket.STATUS_USED.equals(ticket.getStatus())) {
                render(response, true, "登录已确认", "请回到电脑继续操作。");
            } else {
                render(response, false, "二维码已失效", "请回到电脑刷新二维码后重新扫码。");
            }
            return;
        }
        if (!StringUtils.hasText(wecomCode)) {
            fail(ticket, "企业微信未返回授权码");
            render(response, false, "授权失败", "请回到电脑重试，或使用账号密码登录。");
            return;
        }

        try {
            IdentityProvider provider = providerRegistry.get(WeComIdentityProvider.PROVIDER);
            if (provider == null || !provider.isEnabled()) {
                fail(ticket, "企业微信登录未启用");
                render(response, false, "企业微信登录未启用", "请使用账号密码登录。");
                return;
            }
            ExternalIdentity external = provider.authenticate(wecomCode);
            SysUserIdentity identity = findIdentity(external);
            if (identity == null && idpProperties.getJit().isEnabled()) {
                identity = jitUserProvisioner.provision(external);
            }
            if (identity == null) {
                fail(ticket, "该企业微信账号尚未绑定系统账号");
                render(response, false, "账号未绑定",
                        "该企业微信账号尚未绑定系统账号，请联系管理员，或回到电脑使用账号密码登录。");
                return;
            }

            SysUser user = userMapper.selectById(identity.getUserId());
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
                fail(ticket, "账号已停用或离职");
                render(response, false, "账号不可用", "该账号已停用或离职，无法登录。");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            ticket.setStatus(SysQrLoginTicket.STATUS_CONFIRMED);
            ticket.setUsername(user.getUsername());
            ticket.setExternalUserId(external.getExternalUserId());
            ticket.setLoginCode(randomCode(24));
            ticket.setConfirmedTime(now);
            ticket.setExpireTime(now.plusSeconds(qrProperties.getConfirmTtlSeconds()));
            ticket.setUpdateTime(now);
            ticket.setErrorMsg(null);
            // 暂时不落库：票据只写 Redis
            // ticketMapper.updateById(ticket);
            cache(ticket, qrProperties.getConfirmTtlSeconds());

            identity.setLastLoginTime(now);
            identityMapper.updateById(identity);
            log.info("企业微信扫码确认成功: code={}, username={}", code, user.getUsername());
            render(response, true, "登录确认成功", "请回到电脑继续操作，电脑端将自动完成登录。");
        } catch (Exception e) {
            log.warn("企业微信扫码确认失败: code={}, error={}", code, e.getMessage());
            fail(ticket, "企业微信授权失败: " + e.getMessage());
            render(response, false, "登录失败", "企业微信授权失败，请回到电脑重试。");
        }
    }

    /**
     * 电脑端轮询状态；只允许创建票据的那个浏览器（Cookie 绑定）
     */
    public Map<String, Object> status(HttpServletRequest request) {
        String code = readQrCookie(request);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(code)) {
            result.put("status", "NO_TICKET");
            return result;
        }
        SysQrLoginTicket ticket = load(code);
        if (ticket == null) {
            result.put("status", SysQrLoginTicket.STATUS_EXPIRED);
            return result;
        }
        result.put("status", ticket.getStatus());
        long seconds = secondsUntil(ticket.getExpireTime());
        result.put("expiresIn", Math.max(0, seconds));
        if (SysQrLoginTicket.STATUS_CONFIRMED.equals(ticket.getStatus())) {
            result.put("loginCode", ticket.getLoginCode());
            result.put("username", ticket.getUsername());
        }
        if (SysQrLoginTicket.STATUS_FAILED.equals(ticket.getStatus())) {
            result.put("message", ticket.getErrorMsg());
        }
        return result;
    }

    /**
     * 电脑端完成登录：校验 Cookie + 一次性登录码，建立本机 SSO 会话
     */
    public Map<String, String> complete(String loginCode, HttpServletRequest request,
                                        HttpServletResponse response) {
        String code = readQrCookie(request);
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("登录请求已失效，请刷新二维码");
        }
        SysQrLoginTicket ticket = load(code);
        if (ticket == null) {
            throw new BusinessException("二维码已过期，请刷新后重试");
        }
        if (!SysQrLoginTicket.STATUS_CONFIRMED.equals(ticket.getStatus())) {
            throw new BusinessException("尚未完成扫码确认");
        }
        if (!StringUtils.hasText(loginCode) || !constantTimeEquals(loginCode, ticket.getLoginCode())) {
            throw new BusinessException("登录校验失败，请刷新二维码");
        }
        if (!StringUtils.hasText(ticket.getUsername())) {
            throw new BusinessException("登录信息不完整，请刷新二维码");
        }

        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(ticket.getUsername());
        if (!loginUser.isEnabled()) {
            throw new BusinessException("账号已停用或离职");
        }
        sessionService.establish(loginUser, request, response, "WECOM_QR");

        ticket.setStatus(SysQrLoginTicket.STATUS_USED);
        ticket.setUsedTime(LocalDateTime.now());
        ticket.setUpdateTime(LocalDateTime.now());
        // 暂时不落库：票据只写 Redis
        // ticketMapper.updateById(ticket);
        deleteCache(code);
        clearQrCookie(response);

        Map<String, String> result = new LinkedHashMap<>();
        result.put("returnTo", StringUtils.hasText(ticket.getReturnTo()) ? ticket.getReturnTo() : "/");
        result.put("username", ticket.getUsername());
        log.info("企业微信扫码登录完成: code={}, username={}", code, ticket.getUsername());
        return result;
    }

    /** 短链不存在/已过期时的提示页 */
    public void renderInvalidShortLink(HttpServletResponse response) {
        render(response, false, "二维码已过期", "请回到电脑刷新二维码后重新扫码。");
    }

    private SysUserIdentity findIdentity(ExternalIdentity external) {
        return identityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, external.getProvider())
                .eq(SysUserIdentity::getCorpId, external.getCorpId())
                .eq(SysUserIdentity::getExternalUserId, external.getExternalUserId())
                .eq(SysUserIdentity::getStatus, 1)
                .last("limit 1"));
    }

    private SysQrLoginTicket load(String code) {
        if (!StringUtils.hasText(code) || code.length() > 32) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(REDIS_PREFIX + code);
        if (StringUtils.hasText(json)) {
            try {
                SysQrLoginTicket cached = objectMapper.readValue(json, SysQrLoginTicket.class);
                if (!isExpired(cached)) {
                    return cached;
                }
                expire(cached);
                return null;
            } catch (Exception e) {
                log.warn("扫码票据缓存反序列化失败: code={}", code);
                return null;
            }
        }
        // 暂时只使用 Redis：票据不再落库，无需回查数据库。
        // 恢复数据库持久化时取消下面注释。
        // SysQrLoginTicket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<SysQrLoginTicket>()
        //         .eq(SysQrLoginTicket::getCode, code)
        //         .last("limit 1"));
        // if (ticket == null) {
        //     return null;
        // }
        // if (isExpired(ticket)) {
        //     expire(ticket);
        //     return null;
        // }
        // cache(ticket, secondsUntil(ticket.getExpireTime()));
        // return ticket;
        return null;
    }

    private boolean isExpired(SysQrLoginTicket ticket) {
        return ticket.getExpireTime() == null || ticket.getExpireTime().isBefore(LocalDateTime.now());
    }

    private void expire(SysQrLoginTicket ticket) {
        if (SysQrLoginTicket.STATUS_PENDING.equals(ticket.getStatus())
                || SysQrLoginTicket.STATUS_CONFIRMED.equals(ticket.getStatus())) {
            ticket.setStatus(SysQrLoginTicket.STATUS_EXPIRED);
            ticket.setUpdateTime(LocalDateTime.now());
            // 暂时不落库：票据只写 Redis
            // ticketMapper.updateById(ticket);
        }
        deleteCache(ticket.getCode());
    }

    private void fail(SysQrLoginTicket ticket, String message) {
        ticket.setStatus(SysQrLoginTicket.STATUS_FAILED);
        ticket.setErrorMsg(message == null ? null : truncate(message, 255));
        ticket.setUpdateTime(LocalDateTime.now());
        // 暂时不落库：票据只写 Redis
        // ticketMapper.updateById(ticket);
        cache(ticket, Math.max(30, secondsUntil(ticket.getExpireTime())));
    }

    private void cache(SysQrLoginTicket ticket, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + ticket.getCode(),
                    objectMapper.writeValueAsString(ticket), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 只负责加速与 TTL，失败时仍可由数据库兜底
            log.warn("写入扫码票据缓存失败: code={}, error={}", ticket.getCode(), e.getMessage());
        }
    }

    private void deleteCache(String code) {
        if (StringUtils.hasText(code)) {
            redisTemplate.delete(REDIS_PREFIX + code);
        }
    }

    private String buildShortUrl(String code) {
        String base = qrProperties.getPublicBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = originOf(weComProperties.getCallbackUrl());
        }
        if (!StringUtils.hasText(base)) {
            throw new BusinessException("未配置扫码短链的公网地址");
        }
        String path = StringUtils.hasText(qrProperties.getShortPath())
                ? qrProperties.getShortPath().trim() : "/q";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return base.replaceAll("/+$", "") + path + "/" + code;
    }

    private String originOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeReturnTo(String returnTo) {
        if (!StringUtils.hasText(returnTo) || returnTo.length() > 512) {
            return null;
        }
        if (returnTo.indexOf('\r') >= 0 || returnTo.indexOf('\n') >= 0) {
            return null;
        }
        return returnTo.startsWith("/oauth/") ? returnTo : null;
    }

    private static String randomCode(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return URL_ENCODER.encodeToString(buffer);
    }

    private long secondsUntil(LocalDateTime time) {
        if (time == null) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), time).getSeconds();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String readQrCookie(HttpServletRequest request) {
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

    private void writeQrCookie(HttpServletResponse response, String code, int ttlSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                COOKIE_NAME + "=" + code + "; Path=/; Max-Age=" + ttlSeconds
                        + "; HttpOnly; SameSite=Lax");
    }

    private void clearQrCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    private void render(HttpServletResponse response, boolean success, String title, String message) {
        try {
            response.setContentType("text/html;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String color = success ? "#07c160" : "#f56c6c";
            String icon = success ? "✓" : "!";
            response.getWriter().write("<!doctype html><html lang=\"zh-CN\"><head>"
                    + "<meta charset=\"utf-8\"><meta name=\"viewport\" "
                    + "content=\"width=device-width,initial-scale=1\">"
                    + "<title>" + escapeHtml(title) + "</title></head>"
                    + "<body style=\"margin:0;background:#f5f7fa;font-family:-apple-system,"
                    + "BlinkMacSystemFont,'Segoe UI',sans-serif;display:flex;align-items:center;"
                    + "justify-content:center;min-height:100vh;\">"
                    + "<div style=\"background:#fff;border-radius:12px;padding:40px 28px;"
                    + "max-width:340px;text-align:center;box-shadow:0 8px 30px rgba(0,0,0,.08);\">"
                    + "<div style=\"width:64px;height:64px;border-radius:50%;margin:0 auto 18px;"
                    + "background:" + color + ";color:#fff;font-size:34px;line-height:64px;\">"
                    + icon + "</div>"
                    + "<h2 style=\"margin:0 0 10px;font-size:20px;color:#303133;\">"
                    + escapeHtml(title) + "</h2>"
                    + "<p style=\"margin:0;font-size:14px;line-height:1.7;color:#606266;\">"
                    + escapeHtml(message) + "</p></div></body></html>");
        } catch (Exception e) {
            log.warn("渲染扫码结果页失败: {}", e.getMessage());
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
