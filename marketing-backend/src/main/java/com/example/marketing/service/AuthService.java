package com.example.marketing.service;

import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import com.example.marketing.config.OAuth2ClientProperties;
import com.example.marketing.dto.LoginInfoResponse;
import com.example.marketing.dto.TokenResponse;
import com.example.marketing.entity.MarketingMenu;
import com.example.marketing.entity.MarketingUser;
import com.example.marketing.mapper.MarketingMenuMapper;
import com.example.marketing.mapper.MarketingUserMapper;
import com.example.marketing.security.LoginUser;
import com.example.marketing.security.OAuth2TokenClient;
import com.example.marketing.security.SecurityUtils;
import com.example.marketing.security.SuperAdmin;
import com.example.marketing.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 登录授权
 * <p>
 * 本系统没有登录页与用户体系：账号密码由 rbac-oauth 校验（密码模式），
 * 或由用户在 rbac-oauth 完成授权（授权码模式）。两种方式最终都拿到同一个 OAuth2 令牌，
 * 令牌里的 user_name 就是本系统识别用户的唯一依据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuth2TokenClient tokenClient;
    private final OAuth2ClientProperties properties;
    private final MarketingUserMapper userMapper;
    private final MarketingMenuMapper menuMapper;

    /**
     * 密码模式登录
     */
    public TokenResponse loginByPassword(String username, String password) {
        Map<String, Object> token = tokenClient.passwordGrant(username, password);
        return toTokenResponse(token);
    }

    /**
     * 授权码模式登录：用回调地址上的 code 换取令牌
     */
    public TokenResponse loginByCode(String code) {
        Map<String, Object> token = tokenClient.authorizationCodeGrant(code);
        return toTokenResponse(token);
    }

    /**
     * 刷新令牌
     */
    public TokenResponse refresh(String refreshToken) {
        Map<String, Object> token = tokenClient.refreshTokenGrant(refreshToken);
        return toTokenResponse(token);
    }

    /**
     * 授权码模式的跳转地址（前端直接 window.location.href 过去）
     * <p>
     * 由后端拼装，避免 client_id / redirect_uri 在前端散落多处、改一处漏一处。
     * 本项目定制的授权服务要求授权地址上带 client_secret 并会校验。
     */
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(properties.getServerUrl() + "/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("client_secret", properties.getClientSecret())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state == null ? "" : state)
                .build()
                .toUriString();
    }

    /**
     * 当前登录用户的信息：身份 + 角色 + 权限点 + 菜单树
     */
    public LoginInfoResponse getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        MarketingUser user = loginUser.getUser();
        List<MarketingMenu> menus = SuperAdmin.matches(loginUser.getRoles())
                ? menuMapper.selectAllEnabledMenus()
                : menuMapper.selectMenusByUserId(user.getId());

        LoginInfoResponse response = new LoginInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(loginUser.getRoles());
        response.setPermissions(loginUser.getPermissions());
        response.setMenus(TreeUtils.buildTree(menus));
        return response;
    }

    /**
     * 授权服务已签出令牌，还要确认这个人在本系统的同步表里、且在职。
     * <p>
     * 令牌有效只说明「他是 RBAC 的合法用户」，不代表「他被授权使用营销系统」——
     * 后者取决于 RBAC 是否给他配了本子系统的菜单/权限（经 MQ 下发）。
     * 同步表里没有他，就直接拒掉，不把一个用不了的令牌发给前端。
     */
    private TokenResponse toTokenResponse(Map<String, Object> token) {
        String accessToken = asString(token.get("access_token"));
        if (accessToken == null) {
            throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
        }
        requireSyncedUser(accessToken);

        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(asString(token.get("refresh_token")));
        response.setTokenType(asString(token.get("token_type")));
        response.setScope(asString(token.get("scope")));
        Object expiresIn = token.get("expires_in");
        response.setExpiresIn(expiresIn instanceof Number ? ((Number) expiresIn).longValue() : null);
        return response;
    }

    private void requireSyncedUser(String accessToken) {
        Map<String, Object> info = tokenClient.checkToken(accessToken);
        Object username = info == null ? null : info.get("user_name");
        if (username == null) {
            throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
        }
        MarketingUser user = userMapper.selectByUsername(username.toString());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            log.warn("账号已通过授权服务认证，但不在本系统的同步用户表中: username={}", username);
            throw new BusinessException(ResultCode.USER_NOT_SYNCED);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
