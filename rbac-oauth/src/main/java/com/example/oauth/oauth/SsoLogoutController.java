package com.example.oauth.oauth;

import com.example.oauth.common.Result;
import com.example.oauth.entity.ClientDetails;
import com.example.oauth.entity.SsoSessionRecord;
import com.example.oauth.mapper.ClientDetailsMapper;
import com.example.oauth.security.LoginUser;
import com.example.oauth.security.OauthAuthStore;
import com.example.oauth.security.RedisRequestCache;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.service.SsoSessionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一登出（Single Logout）
 * <p>
 * 门户与各子系统都调这一个地址。它做两件事，<b>两件都必须做</b>：
 * <ol>
 *   <li><b>删掉 SSO 会话</b>（{@code sso:session:{username}}）。授权服务签发的令牌是自包含 JWT，
 *       删不掉也收不回；但 {@code /oauth/check_token} 会要求会话存在，所以这一步过后，
 *       该用户在<b>所有</b>子系统手里的旧令牌下一个请求就失效 —— 不需要逐个通知子系统。</li>
 *   <li><b>销毁浏览器会话</b>（Spring Session，{@code rbac:session:*}）。
 *       只做第 1 步是不够的：浏览器里那个 SESSION 还在，拿它就能静默重走一遍授权码流程、
 *       换出一张新令牌，而签发新令牌会<b>重建</b> SSO 会话 —— 统一登出当场被绕过。</li>
 * </ol>
 * <p>
 * 另外顺手清掉这次登录流程的临时状态：待授权请求缓存（Redis {@code oauth2:reqcache:*}）
 * 及其索引 Cookie {@code RBAC_OAUTH2_REQ} —— 它记录着「要授权哪个客户端」，登出后没有保留的意义。
 *
 * <h3>两种调用方式</h3>
 * <ul>
 *   <li>{@code POST /oauth/sso-logout} —— XHR/fetch 用（门户的「统一登出」按钮），返回 JSON。</li>
 *   <li>{@code GET /oauth/sso-logout?redirect_uri=...} —— 整页跳转用（各子系统的登出按钮）。
 *       子系统前端要清掉浏览器 SESSION 只能让<b>浏览器自己</b>来发请求，所以这里必须支持导航访问；
 *       处理完 302 回 {@code redirect_uri}（不传则回授权服务的登录页）。</li>
 * </ul>
 * {@code redirect_uri} 只允许跳到<b>已登记客户端的回调地址所在的源</b>（见
 * {@link #isAllowedRedirectTarget}），否则就是一个任人利用的开放重定向。
 *
 * <h3>身份从哪来</h3>
 * 按顺序尝试，取到就停：
 * <ol>
 *   <li>浏览器 Cookie {@code RBAC_OAUTH2_AUTH} 指向的 Redis 登录态（{@link OauthAuthStore}）——
 *       用户在这台浏览器上登录过就走这条；</li>
 *   <li>请求头 {@code Authorization: Bearer <access token>} 里的 {@code user_name} 声明 ——
 *       子系统用密码模式登录时浏览器里没有登录态，只能靠令牌认人。</li>
 * </ol>
 * 两者都取不到就按「本来就没登录」处理，直接返回成功：登出应当是<b>幂等</b>的，
 * 重复调用、登录态已过期时调都不该报错。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SsoLogoutController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_USER_NAME = "user_name";

    private final SsoSessionStore ssoSessionStore;
    private final OauthAuthStore oauthAuthStore;
    private final SsoSessionRecordService ssoSessionRecordService;
    private final RedisRequestCache requestCache;
    private final TokenStore jwtTokenStore;
    private final ClientDetailsMapper clientDetailsMapper;

    /** 未指定 redirect_uri 时回到授权服务的登录页 */
    @Value("${oauth2.login-page-url}")
    private String loginPageUrl;

    @PostMapping("/oauth/sso-logout")
    public Result<Map<String, Object>> ssoLogout(HttpServletRequest request,
                                                 HttpServletResponse response) {
        Map<String, Object> data = performLogout(request, response);
        return Result.success("已登出", data);
    }

    /**
     * 整页跳转版：清完会话后 302 回子系统/门户自己的页面。
     * <p>
     * 不返回 JSON —— 这是浏览器导航，用户会直接看到响应体。
     */
    @GetMapping("/oauth/sso-logout")
    public void ssoLogoutByNavigation(@RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = performLogout(request, response);

        String target = isAllowedRedirectTarget(redirectUri) ? redirectUri : loginPageUrl;
        log.info("统一登出（整页跳转）: username={}, 跳回={}", data.get("username"), target);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, target);
    }

    private Map<String, Object> performLogout(HttpServletRequest request, HttpServletResponse response) {
        String token = OauthAuthStore.readToken(request);
        String username = resolveUsername(request, token);

        boolean sessionRemoved = false;
        if (StringUtils.hasText(username)) {
            ssoSessionStore.delete(username);
            sessionRemoved = true;
            log.info("统一登出：已清除 SSO 会话，该用户所有子系统令牌即刻失效: username={}", username);
        }

        // 删掉 Redis 里那条认证记录，并让浏览器丢掉 Cookie —— 否则它还能凭旧 Cookie 再走一遍授权
        boolean loginStateRemoved = false;
        if (StringUtils.hasText(token)) {
            oauthAuthStore.delete(token);
            loginStateRemoved = true;
        }
        OauthAuthStore.clearCookie(response);

        // 待授权请求的缓存（Redis oauth2:reqcache:* + Cookie RBAC_OAUTH2_REQ）属于这次登录流程的
        // 临时状态，登出后一并清掉：否则它会带着「要授权哪个客户端」的信息残留到 TTL 到期
        requestCache.removeRequest(request, response);

        // 落库：把这次登录对应的会话记录标成「已登出」（没有对应记录时是空操作）
        if (StringUtils.hasText(username)) {
            ssoSessionRecordService.closeOnlineRows(username,
                    SsoSessionRecord.STATUS_OFFLINE, "主动登出");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("ssoSessionRemoved", sessionRemoved);
        data.put("loginStateRemoved", loginStateRemoved);
        return data;
    }

    /**
     * redirect_uri 白名单：只允许已登记客户端的回调地址所在的「源」（协议 + 主机 + 端口）。
     * <p>
     * 为什么用回调地址推：这些地址本来就登记在 clientdetails 里、由各个客户端自己维护，
     * 从这里推导不需要再维护一份「允许登出后跳回哪里」的配置；而能收到授权码的地方，
     * 本来就是这套 SSO 里可信的一端。必须核对<b>源</b>而不是整个地址 ——
     * 否则例如 {@code http://localhost:5176/oauth/callback?x=...} 这种变形就能绕过前缀比较。
     */
    private boolean isAllowedRedirectTarget(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            return false;
        }
        String targetOrigin = originOf(redirectUri);
        if (targetOrigin == null) {
            return false;
        }
        for (String origin : registeredOrigins()) {
            if (origin.equals(targetOrigin)) {
                return true;
            }
        }
        log.warn("登出后的跳转目标不在已登记客户端的来源里，拒绝跳转: {}", redirectUri);
        return false;
    }

    private Set<String> registeredOrigins() {
        Set<String> origins = new HashSet<>();
        List<ClientDetails> clients = clientDetailsMapper.selectList(null);
        for (ClientDetails client : clients) {
            if (!StringUtils.hasText(client.getRedirectUrl())) {
                continue;
            }
            for (String url : client.getRedirectUrl().split(",")) {
                String origin = originOf(url.trim());
                if (origin != null) {
                    origins.add(origin);
                }
            }
        }
        return origins;
    }

    private String originOf(String url) {
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                origin.append(':').append(uri.getPort());
            }
            return origin.toString();
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 先看浏览器会话里的登录态，再退回令牌。返回 null 表示这次请求本来就带着「未登录」的身份。
     */
    private String resolveUsername(HttpServletRequest request, String cookieToken) {
        LoginUser fromCookie = oauthAuthStore.read(cookieToken);
        if (fromCookie != null) {
            return fromCookie.getUsername();
        }
        return usernameFromBearerToken(request);
    }

    private String usernameFromBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String tokenValue = header.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(tokenValue)) {
            return null;
        }
        try {
            // 这里刻意用原始的 JwtTokenStore 而不是 SsoTokenServices：登出要幂等，
            // 会话已经被删掉时也得能从令牌里认出「是谁在登出」，才能继续清浏览器登录态。
            OAuth2AccessToken token = jwtTokenStore.readAccessToken(tokenValue);
            Map<String, Object> info = token == null ? null : token.getAdditionalInformation();
            Object username = info == null ? null : info.get(CLAIM_USER_NAME);
            return username == null ? null : username.toString();
        }
        catch (Exception e) {
            // 令牌非法/过期：不报错，按未登录处理（登出对客户端而言永远是成功的）
            log.debug("登出时令牌不可用，忽略: {}", e.getMessage());
            return null;
        }
    }

}
