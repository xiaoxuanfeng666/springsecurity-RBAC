package com.example.oauth.oauth;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.SsoSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 带 SSO 会话校验的令牌服务
 * <p>
 * 授权服务签发的 access token 是自包含 JWT，验签即可通过 —— 服务端手里没有它的任何记录，
 * 所以「登出」这件事在纯 JWT 方案里根本无处落地。本类在库的 {@link DefaultTokenServices}
 * 外面套了一层 Redis SSO 会话（{@link SsoSessionStore}），让令牌的生死重新由服务端说了算：
 *
 * <table border="1">
 *   <caption>三个覆写点各管什么</caption>
 *   <tr><th>方法</th><th>触发时机</th><th>做什么</th></tr>
 *   <tr>
 *     <td>{@link #createAccessToken(OAuth2Authentication)}</td>
 *     <td>密码 / 授权码 / 隐式模式首次签发令牌</td>
 *     <td>建立或续期 SSO 会话，并把本次的客户端记进会话</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #refreshAccessToken(String, TokenRequest)}</td>
 *     <td>刷新令牌换新令牌</td>
 *     <td>会话已不存在则直接拒绝（{@code invalid_grant}）；存在则续期 TTL</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #readAccessToken(String)} / {@link #loadAuthentication(String)}</td>
 *     <td>{@code /oauth/check_token} 校验令牌</td>
 *     <td>会话不存在就当作令牌无效 → 子系统立刻收到 401</td>
 *   </tr>
 * </table>
 *
 * <h3>为什么把校验放在 readAccessToken，而不是自己写一个 /oauth/check_token</h3>
 * 自己写一个 {@code @RestController} 映射 {@code /oauth/check_token} 会和库自带的
 * {@code CheckTokenEndpoint} 撞上同一个路径，启动时直接报 Ambiguous mapping。
 * 而库的 {@code CheckTokenEndpoint} 第一步就是调 {@code readAccessToken}，返回 null 即抛
 * {@code InvalidTokenException}；把它覆写掉既不用碰端点，又天然覆盖了子系统那条链路。
 *
 * <h3>为什么刷新时用 touch 而不是 createOrRenew</h3>
 * 用 createOrRenew 的话，「登出 → 拿旧刷新令牌再刷一次」会把会话重新建出来，统一登出当场被绕过。
 * touch 只在键已存在时延长 TTL，绝不新建，因此登出之后刷新令牌也救不回来。
 */
@Slf4j
public class SsoTokenServices extends DefaultTokenServices {

    private static final String CLAIM_USER_NAME = "user_name";

    private final SsoSessionStore ssoSessionStore;
    private final TokenStore tokenStore;

    public SsoTokenServices(SsoSessionStore ssoSessionStore, TokenStore tokenStore) {
        this.ssoSessionStore = ssoSessionStore;
        this.tokenStore = tokenStore;
    }

    /**
     * 首次签发令牌：建立/续期 SSO 会话。
     * <p>
     * 客户端凭证模式（client_credentials）的 principal 是客户端 id 字符串而非 {@link LoginUser}，
     * 没有用户也就没有 SSO 会话，这里直接跳过 —— 该模式的令牌不受统一登出影响。
     * <p>
     * 注意刷新路径**不会**走到这里：库的 {@code refreshAccessToken} 调的是受保护的二参
     * {@code createAccessToken(authentication, refreshToken)}，与本方法无关。
     */
    @Override
    public OAuth2AccessToken createAccessToken(OAuth2Authentication authentication) throws AuthenticationException {
        OAuth2AccessToken token = super.createAccessToken(authentication);

        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) principal;
            ssoSessionStore.createOrRenew(loginUser.getUsername(),
                    loginUser.getUser().getId(),
                    loginUser.getUser().getNickname(),
                    authentication.getOAuth2Request().getClientId());
            log.debug("SSO 会话已建立/续期: username={}, clientId={}",
                    loginUser.getUsername(), authentication.getOAuth2Request().getClientId());
        }
        return token;
    }

    /**
     * 刷新令牌：会话已结束则拒绝续期，否则续期 TTL。
     * <p>
     * 先判会话再调 super —— 顺序不能反过来，否则已经登出的人还能拿到一张新令牌，
     * 即便那张令牌随后会被 check_token 拒绝，「登出后仍能换出令牌」本身就是不该出现的行为。
     */
    @Override
    public OAuth2AccessToken refreshAccessToken(String refreshTokenValue, TokenRequest tokenRequest)
            throws AuthenticationException {
        String username = resolveUsernameOfRefreshToken(refreshTokenValue);
        if (username != null && !ssoSessionStore.exists(username)) {
            log.info("拒绝刷新：SSO 会话已结束（统一登出或已被踢下线）: username={}", username);
            throw new InvalidGrantException("登录会话已结束，请重新登录");
        }

        OAuth2AccessToken token = super.refreshAccessToken(refreshTokenValue, tokenRequest);

        if (username != null) {
            ssoSessionStore.touch(username);
        }
        return token;
    }

    /**
     * 校验访问令牌：会话不存在一律按无效处理。
     * <p>
     * 这是统一登出生效的关键一环 ---- 登出只删了 Redis 里那一个键，而所有子系统持有的旧令牌
     * 从这里开始全部失效，无需逐个通知子系统。
     */
    @Override
    public OAuth2AccessToken readAccessToken(String accessToken) {
        OAuth2AccessToken token = super.readAccessToken(accessToken);
        if (token == null) {
            return null;
        }
        return isSessionAlive(token) ? token : null;
    }

    /**
     * 同上，覆盖另一条入口：库的 {@code CheckTokenEndpoint} 取完令牌还会再调一次
     * {@code loadAuthentication}，而它内部直接走 {@code tokenStore.readAccessToken}，
     * 绕过了上面那个覆写。两端都拦一遍，这套校验才不依赖调用顺序。
     * <p>
     * <b>身份必须从令牌声明里取，不能用 {@code authentication.getName()}</b>：
     * 客户端凭证模式的 {@code OAuth2Authentication} 没有用户认证，
     * {@code getName()} 会退化成<b>客户端 id</b>（见 {@code OAuth2Authentication#getPrincipal}），
     * 于是「rbac-third-party」会被当成用户名去查会话，必然查不到 —— 结果就是机器令牌被误判为已登出。
     */
    @Override
    public OAuth2Authentication loadAuthentication(String accessTokenValue)
            throws AuthenticationException, InvalidTokenException {
        OAuth2AccessToken token = null;
        try {
            token = tokenStore.readAccessToken(accessTokenValue);
        }
        catch (Exception e) {
            // 令牌非法/过期：不在这里处理，交给 super 抛标准错误（保持错误信息与库一致）
            log.debug("读取令牌失败，跳过 SSO 会话校验: {}", e.getMessage());
        }
        if (token != null && !isSessionAlive(token)) {
            throw new InvalidTokenException("登录会话已结束，请重新登录");
        }
        return super.loadAuthentication(accessTokenValue);
    }

    /**
     * 会话是否仍然有效。
     * <p>
     * 令牌里没有 {@code user_name} 的（客户端凭证模式）不受约束 —— 它没有用户，
     * 也就没有 SSO 会话可校验，统一登出不该影响它。
     */
    private boolean isSessionAlive(OAuth2AccessToken token) {
        String username = usernameOf(token);
        if (username == null) {
            return true;
        }
        boolean alive = ssoSessionStore.exists(username);
        if (!alive) {
            log.debug("令牌校验未通过：SSO 会话不存在或已登出: username={}", username);
        }
        return alive;
    }

    /**
     * 从令牌声明里取用户名。{@code DefaultAccessTokenConverter} 会无条件写入 {@code user_name}，
     * 且 {@code extractAccessToken} 保留了它（只剔除 exp / aud / client_id / scope），因此这里能直接读到。
     */
    private String usernameOf(OAuth2AccessToken token) {
        Map<String, Object> info = token.getAdditionalInformation();
        if (info == null) {
            return null;
        }
        Object username = info.get(CLAIM_USER_NAME);
        return username == null ? null : username.toString();
    }

    /**
     * 只解析刷新令牌里的用户名，不校验其有效性。
     * <p>
     * 令牌非法 / 过期时返回 null，把「令牌本身有问题」交给 {@code super} 去抛标准错误；
     * 本方法只负责回答「这个用户的会话还在不在」。
     * <p>
     * 客户端凭证模式（没有用户认证）返回 null：它的「身份」是客户端 id 而不是用户
     * （{@code OAuth2Authentication#getName()} 这时会退化成客户端 id），
     * 拿它去查 {@code sso:session:} 必然查不到，会把机器令牌误判成已登出。
     */
    private String resolveUsernameOfRefreshToken(String refreshTokenValue) {
        try {
            OAuth2RefreshToken refreshToken = tokenStore.readRefreshToken(refreshTokenValue);
            if (refreshToken == null) {
                return null;
            }
            OAuth2Authentication authentication = tokenStore.readAuthenticationForRefreshToken(refreshToken);
            if (authentication == null || authentication.isClientOnly()) {
                return null;
            }
            return authentication.getName();
        }
        catch (Exception e) {
            log.debug("刷新令牌解析失败，跳过 SSO 会话校验: {}", e.getMessage());
            return null;
        }
    }
}
