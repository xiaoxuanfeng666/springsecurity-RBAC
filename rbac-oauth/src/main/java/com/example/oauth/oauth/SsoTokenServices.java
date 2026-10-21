package com.example.oauth.oauth;

import com.example.oauth.security.LoginUser;
import com.example.oauth.security.SsoSession;
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
import java.util.UUID;

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
 *     <td>账号已停用/离职/删除、或会话已不存在则直接拒绝（{@code invalid_grant}）；否则续期 TTL</td>
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

    /**
     * 刷新令牌时把「本次刷新对应的 sid」传给同线程内的 createAccessToken。
     * 库的二参 createAccessToken 是 private，无法覆写，因此用 ThreadLocal 传递。
     */
    private static final ThreadLocal<String> REFRESH_SESSION_ID = new ThreadLocal<>();

    /** 供 TokenEnhancer 在刷新路径读取本次刷新的 sid（此路径不经过 1 参 createAccessToken） */
    public static String currentRefreshSessionId() {
        return REFRESH_SESSION_ID.get();
    }

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
        String sessionId = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            LoginUser loginUser = (LoginUser) principal;
            sessionId = resolveSessionId(authentication, loginUser);
            loginUser.setSessionId(sessionId);
            ssoSessionStore.createOrRenew(loginUser.getUsername(),
                    loginUser.getUser().getId(),
                    loginUser.getUser().getNickname(),
                    authentication.getOAuth2Request().getClientId(), sessionId);
            log.debug("SSO 会话已建立/续期: username={}, sid={}, clientId={}",
                    loginUser.getUsername(), sessionId,
                    authentication.getOAuth2Request().getClientId());
        }
        OAuth2AccessToken token = super.createAccessToken(authentication);
        if (sessionId != null && token.getRefreshToken() != null) {
            ssoSessionStore.bindRefreshSid(
                    ((LoginUser) principal).getUsername(), token.getRefreshToken().getValue(), sessionId);
        }
        return token;
    }

    /**
     * 解析本次签发应使用的 sid。
     * <p>
     * 浏览器流程（授权码/隐式）的 principal 来自 {@code oauth2:auth} 登录态，sid 已在其中；
     * 密码模式是一次全新的登录，必须生成新 sid 以踢掉旧会话；刷新路径由下面的二参方法
     * 预先填好当前 sid，避免每次刷新都轮换会话。
     */
    private String resolveSessionId(OAuth2Authentication authentication, LoginUser loginUser) {
        String grantType = authentication.getOAuth2Request() == null
                ? null : authentication.getOAuth2Request().getGrantType();
        if (StringUtils.hasText(loginUser.getSessionId())) {
            String browserSid = loginUser.getSessionId();
            // 授权码/隐式流程：如果旧浏览器登录态已被新登录删除，拒绝其手上的旧授权码，
            // 避免它把刚轮换过去的会话又抢回来。
            if (("authorization_code".equals(grantType) || "implicit".equals(grantType))
                    && !ssoSessionStore.isBrowserSessionAlive(browserSid)) {
                throw new InvalidGrantException("登录会话已结束，请重新登录");
            }
            return browserSid;
        }
        // 刷新路径：沿用刷新令牌对应的 sid，避免每次刷新都轮换
        String refreshSessionId = REFRESH_SESSION_ID.get();
        if (StringUtils.hasText(refreshSessionId)) {
            return refreshSessionId;
        }
        if ("password".equals(grantType)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        SsoSession current = ssoSessionStore.read(loginUser.getUsername());
        if (current != null && StringUtils.hasText(current.getSessionId())) {
            return current.getSessionId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 刷新令牌：会话已结束或刷新令牌已被使用过则拒绝，否则轮换刷新令牌并续期会话。
     * <p>
     * 先判会话再调 super —— 顺序不能反过来，否则已经登出的人还能拿到一张新令牌，
     * 即便那张令牌随后会被 check_token 拒绝，「登出后仍能换出令牌」本身就是不该出现的行为。
     * <p>
     * 刷新令牌是一次性的：这里先用 {@link SsoSessionStore#consumeRefreshSid} 原子取走它的 sid 绑定
     * 并留下「已用」墓碑；取不到时再查墓碑，若墓碑命中说明这张令牌<b>被用第二次</b>，
     * 即令牌可能已泄露，此时撤销该用户整个会话（清会话 + 清全部刷新绑定 + 广播撤权），
     * 让被盗令牌只能在窃取者手里用一次，而不是一直续命。
     * <p>
     * 只有墓碑里的 sid 仍是当前活跃会话时才撤权：用户早已重新登录、换了新会话时，
     * 旧令牌重放不影响新会话，静默拒绝即可，免得攻击者拿历史令牌把在线用户踢下线。
     * <p>
     * 因此旧刷新令牌再次提交会直接得到 invalid_grant，而旧的 access token 不受影响，
     * 仍在自己的有效期内可用 —— 这正是无状态 JWT + 短有效期该有的行为。
     */
    @Override
    public OAuth2AccessToken refreshAccessToken(String refreshTokenValue, TokenRequest tokenRequest)
            throws AuthenticationException {
        String username = resolveUsernameOfRefreshToken(refreshTokenValue);
        String refreshSessionId = null;
        if (username != null) {
            SsoSessionStore.RefreshConsumeResult consumed =
                    ssoSessionStore.consumeRefreshSid(username, refreshTokenValue);
            if (consumed.isReused()) {
                if (ssoSessionStore.matchesSessionId(username, consumed.getSid())) {
                    log.warn("检测到刷新令牌复用，撤销会话并广播撤权: username={}, sid={}",
                            username, consumed.getSid());
                    ssoSessionStore.delete(username);
                } else {
                    log.warn("检测到已用刷新令牌被再次提交，但对应会话已不是当前会话，仅拒绝: username={}", username);
                }
                throw new InvalidGrantException("登录会话已结束，请重新登录");
            }
            refreshSessionId = consumed.getSid();
            if (!ssoSessionStore.matchesSessionId(username, refreshSessionId)) {
                log.info("拒绝刷新：刷新令牌已使用/失效，或会话已在其他浏览器登录: username={}", username);
                throw new InvalidGrantException("登录会话已结束或刷新令牌已失效，请重新登录");
            }
        }

        OAuth2AccessToken token;
        REFRESH_SESSION_ID.set(refreshSessionId);
        try {
            token = super.refreshAccessToken(refreshTokenValue, tokenRequest);
        } finally {
            REFRESH_SESSION_ID.remove();
        }

        if (username != null) {
            ssoSessionStore.touch(username);
            // 刷新令牌已轮换（reuseRefreshToken=false）：新令牌绑定同一个 sid，旧值在上面已被消费
            if (refreshSessionId != null && token.getRefreshToken() != null) {
                ssoSessionStore.bindRefreshSid(
                        username, token.getRefreshToken().getValue(), refreshSessionId);
            }
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
        boolean alive = ssoSessionStore.matchesSessionId(username, sidOf(token));
        if (!alive) {
            log.debug("令牌校验未通过：SSO 会话不存在、已登出或 sid 已被新登录轮换: username={}", username);
        }
        return alive;
    }

    private String sidOf(OAuth2AccessToken token) {
        Map<String, Object> info = token.getAdditionalInformation();
        if (info == null) {
            return null;
        }
        Object sid = info.get("sid");
        return sid == null ? null : sid.toString();
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
