package com.example.oauth.oauth;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.OauthAuthStore;
import com.example.oauth.security.SsoSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

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
            log.debug("已写入登录态并记录浏览器索引: username={}", username);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
