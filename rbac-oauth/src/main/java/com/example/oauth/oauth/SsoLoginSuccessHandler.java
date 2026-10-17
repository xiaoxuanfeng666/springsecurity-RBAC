package com.example.oauth.oauth;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.OauthAuthStore;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.service.SsoSessionRecordService;
import com.example.oauth.util.DeviceInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 授权端点登录成功后的处理：写登录态、记浏览器索引，然后跳回授权页
 * <p>
 * 登录态不再交给 HTTP 会话（那会被 Spring Session 序列化成一团 Java 对象），而是
 * 由 {@link OauthAuthStore} 在 Redis 里写一条精简的认证记录，浏览器只拿一个 token Cookie：
 * <pre>
 *   oauth2:auth:{token}   {"userId":1,"username":"admin","nickname":"超级管理员","loginTime":"..."}
 *   Cookie RBAC_OAUTH2_AUTH = token
 * </pre>
 * <p>
 * 顺手记 {@code sso:browser:{username} → token}（{@link SsoSessionStore}）只为一件事：
 * <b>让后台管理员能踢人</b>。踢人是在 rbac-backend 上点的，那边手上只有 userId、没有对方的
 * Cookie，靠这条反查索引才能删掉 {@code oauth2:auth:{token}}，让对方的下一次授权请求回到登录页。
 * 不记的话，令牌失效了但浏览器登录态还在，拿着它静默重走一遍授权码就能换出新令牌 —— 踢人形同无效。
 * <p>
 * 其余行为沿用父类（含 {@link org.springframework.security.web.savedrequest.RequestCache}
 * 与重定向策略，它们由 {@code OAuth2AuthorizationSecurityConfig} 注入）。
 */
@Slf4j
@RequiredArgsConstructor
public class SsoLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final OauthAuthStore oauthAuthStore;
    private final SsoSessionStore ssoSessionStore;
    private final SsoSessionRecordService ssoSessionRecordService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) principal;
            String username = loginUser.getUsername();

            // 单会话模型（与 rbac-backend 的管理端登录一致）：先清掉上一次的登录态，
            // 否则每登录一次就多留一条 oauth2:auth:* 直到 TTL 到期
            String previousToken = ssoSessionStore.getBrowserSessionId(username);
            if (previousToken != null) {
                oauthAuthStore.delete(previousToken);
            }

            String token = oauthAuthStore.create(loginUser);
            OauthAuthStore.writeCookie(response, token);
            ssoSessionStore.bindBrowserSession(username, token);

            // 落库：登录历史 + 登录设备（IP / User-Agent，只有这里能拿到真实浏览器的信息）
            ssoSessionRecordService.recordLogin(loginUser,
                    DeviceInfoUtils.ip(request), DeviceInfoUtils.userAgent(request));

            log.debug("已写入登录态并记录浏览器索引: username={}", username);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * 决定登录成功后的跳转地址
     * <p>
     * 正常情况下用 Spring Security 的请求缓存（Redis，30 分钟）里保存的授权地址。
     * 但用户在登录页停留超过 30 分钟后缓存会过期，此时退回默认首页 —— 子系统就回不去了。
     * 因此缓存为空时，改读登录页表单带回的 {@code returnTo}（见 {@link OAuth2LoginEntryPoint}）。
     * <p>
     * 还有一种理论上的边界：请求缓存命中，但 {@code savedRequest.getRedirectUrl()} 为 null/空。
     * 父类会把 null 原样返回，随后 {@code DefaultRedirectStrategy} 会在
     * {@code UrlUtils.isAbsoluteUrl(null)} 处抛异常（500）。本项目的
     * {@link com.example.oauth.security.RedisRequestCache} 只在地址非空时才构造
     * {@code SimpleSavedRequest}，走不到这个分支；这里仍做一层兜底：按「缓存未命中」处理，
     * 优先用 {@code returnTo}，再退回默认首页。
     * <p>
     * {@code returnTo} 来自浏览器，必须限制成以 {@code /oauth/} 开头的相对路径：
     * 只允许回到授权端点，既排除了绝对地址与协议相对地址（{@code //host}）造成的开放重定向，
     * 也杜绝了 CRLF 注入。
     */
    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response) {
        String target = super.determineTargetUrl(request, response);
        // 缓存未命中（默认首页）或缓存里的跳转地址为空，都尝试用 returnTo 恢复
        if (!StringUtils.hasText(target) || getDefaultTargetUrl().equals(target)) {
            String returnTo = request.getParameter(OAuth2LoginEntryPoint.RETURN_TO_PARAM);
            if (isAllowedReturnTo(returnTo)) {
                log.info("授权请求缓存已过期，按登录页带回的地址恢复: returnTo={}", returnTo);
                return returnTo;
            }
            // 父类返回 null 时不能再交给重定向策略，否则会因 UrlUtils.isAbsoluteUrl(null) 抛 500
            return getDefaultTargetUrl();
        }
        return target;
    }

    private static boolean isAllowedReturnTo(String returnTo) {
        if (!StringUtils.hasText(returnTo)) {
            return false;
        }
        if (returnTo.indexOf('\r') >= 0 || returnTo.indexOf('\n') >= 0) {
            return false;
        }
        return returnTo.startsWith("/oauth/");
    }
}
