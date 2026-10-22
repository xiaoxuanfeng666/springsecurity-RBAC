package com.example.audit.service;

import com.example.audit.common.BusinessException;
import com.example.audit.common.ResultCode;
import com.example.audit.config.OAuth2ClientProperties;
import com.example.audit.dto.LoginInfoResponse;
import com.example.audit.entity.AuditMenu;
import com.example.audit.entity.AuditUser;
import com.example.audit.mapper.AuditMenuMapper;
import com.example.audit.mapper.AuditUserMapper;
import com.example.audit.security.BffClientFingerprint;
import com.example.audit.security.BffSession;
import com.example.audit.security.BffSessionCookie;
import com.example.audit.security.BffSessionStore;
import com.example.audit.security.JwtTokenVerifier;
import com.example.audit.security.LoginUser;
import com.example.audit.security.OAuth2StateStore;
import com.example.audit.security.OAuth2TokenClient;
import com.example.audit.security.SecurityUtils;
import com.example.audit.security.SuperAdmin;
import com.example.audit.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 登录授权（BFF 模式）
 * <p>
 * 本系统没有登录页与用户体系：用户在 rbac-oauth 完成授权（授权码模式）。
 * 令牌<b>只留在服务端</b>：写进 Redis 的 BFF 会话，浏览器只拿到一个 HttpOnly 的会话 Cookie。
 * <p>
 * 令牌里的 user_name 是识别用户的唯一依据；refresh token 只由 {@code OAuth2TokenAuthenticationFilter}
 * 在 access token 过期时使用，前端无从接触。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuth2TokenClient tokenClient;
    private final OAuth2ClientProperties properties;
    private final OAuth2StateStore stateStore;
    private final AuditUserMapper userMapper;
    private final AuditMenuMapper menuMapper;
    private final JwtTokenVerifier jwtTokenVerifier;
    private final BffSessionStore sessionStore;
    private final BffSessionCookie sessionCookie;

    /** 会话绝对有效期（秒）：从登录时刻算起，到点必须重新授权 */
    @Value("${rbac.bff.session-max-ttl-seconds:43200}")
    private long sessionMaxTtlSeconds;

    /** 二次验证有效期（秒）：验证通过后这段时间内可执行敏感操作 */
    @Value("${rbac.bff.elevate-ttl-seconds:300}")
    private long elevateTtlSeconds;

    /**
     * 授权码模式登录：用回调地址上的 code 换取令牌
     * <p>
     * 先消费 state 再换令牌：state 必须来自本次 {@code /auth/sso-login} 签发，
     * 且只能使用一次。这样重放回调地址（或伪造回调）在换取令牌之前就被拒绝。
     */
    public LoginInfoResponse loginByCode(String code, String state,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (!stateStore.consume(state, request, response)) {
            throw new BusinessException(ResultCode.STATE_INVALID);
        }
        return establishSession(tokenClient.authorizationCodeGrant(code), request, response);
    }

    /**
     * 登出：先用会话里的 access token 让授权服务销毁 SSO 会话（所有子系统立即失效），
     * 再删本地会话、清 Cookie。没有会话时也正常返回（登出是幂等的）。
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = sessionCookie.read(request);
        if (StringUtils.hasText(sessionId)) {
            BffSession session = sessionStore.get(sessionId);
            sessionStore.delete(sessionId);
            if (session != null && StringUtils.hasText(session.getAccessToken())) {
                tokenClient.ssoLogout(session.getAccessToken());
            }
            log.info("已退出 BFF 会话: username={}, sessionId={}",
                    session == null ? null : session.getUsername(), BffSession.mask(sessionId));
        }
        sessionCookie.clear(response);
    }

    /**
     * 授权码模式的授权地址（由本系统**后端**拼装，并由后端直接发起跳转）
     * <p>
     * 地址里只有 {@code client_id / redirect_uri / scope / state}。按 OAuth2 标准，
     * {@code client_secret} 不进入浏览器地址栏：它只在本系统后端调 {@code /oauth/token}
     * 换令牌时通过 HTTP Basic 提交。前端只做一次「导航」到 {@code /auth/sso-login}，
     * URL 由后端拼、302 由后端发。
     * <p>
     * 浏览器访问的地址走 {@code oauth2.browser-authorize-base-url}（授权前端 5174，与登录页同源）；
     * 后端自己调令牌端点仍用 {@code serverUrl}（7071），那条是服务端到服务端。
     */
    public String buildAuthorizeUrl(HttpServletResponse response) {
        String base = StringUtils.hasText(properties.getBrowserAuthorizeBaseUrl())
                ? properties.getBrowserAuthorizeBaseUrl()
                : properties.getServerUrl();
        String effectiveState = stateStore.issue(response);
        return UriComponentsBuilder.fromHttpUrl(base + "/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", effectiveState)
                .build()
                .toUriString();
    }


    /**
     * 敏感操作二次验证：校验密码后给当前会话打上有效期标记
     * <p>
     * 密码错误只拒绝本次操作，不影响登录态；连续失败次数由网关/后续限流控制。
     */
    public void elevate(String password, HttpServletRequest request) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!tokenClient.verifyPassword(loginUser.getUsername(), password)) {
            throw new BusinessException(ResultCode.REAUTH_PASSWORD_ERROR);
        }
        BffSessionStore.ResolvedSession resolved = sessionStore.resolve(sessionCookie.read(request));
        if (resolved == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        BffSession session = resolved.getSession();
        session.setElevatedUntil(System.currentTimeMillis() + elevateTtlSeconds * 1000L);
        sessionStore.save(session);
        log.info("敏感操作二次验证通过: username={}", loginUser.getUsername());
    }

    /**
     * 当前登录用户的信息：身份 + 角色 + 权限点 + 菜单树
     */
    public LoginInfoResponse getInfo() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return buildInfo(loginUser.getUser(), loginUser.getRoles(), loginUser.getPermissions());
    }

    /**
     * 换到令牌后建立服务端会话，并把会话 id 写进 HttpOnly Cookie。
     * <p>
     * 令牌有效只说明「他是 RBAC 的合法用户」，不代表「他被授权使用审计日志系统」——
     * 后者取决于 RBAC 是否给他配了本子系统的菜单/权限（经 MQ 下发）。
     * 同步表里没有他就直接拒掉，不建立一个用不了的会话。
     */
    private LoginInfoResponse establishSession(Map<String, Object> token, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = asString(token.get("access_token"));
        String refreshToken = asString(token.get("refresh_token"));
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
        }

        JwtTokenVerifier.VerifyResult verified = jwtTokenVerifier.verify(accessToken);
        if (!verified.isValid()) {
            log.warn("授权服务签发的令牌无法通过本地验签，拒绝建立会话");
            throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
        }
        String username = verified.getUsername();
        AuditUser user = userMapper.selectByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            log.warn("账号已通过授权服务认证，但不在本系统的同步用户表中: username={}", username);
            throw new BusinessException(ResultCode.USER_NOT_SYNCED);
        }

        long now = System.currentTimeMillis();
        BffSession session = new BffSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setLoginTime(now);
        session.setExpireAt(now + sessionMaxTtlSeconds * 1000L);
        session.setClientFingerprint(BffClientFingerprint.of(request));
        sessionStore.save(session);
        sessionCookie.write(response, session.getSessionId());
        log.info("已建立 BFF 会话: username={}, sessionId={}", username, BffSession.mask(session.getSessionId()));

        return buildInfo(user);
    }

    private LoginInfoResponse buildInfo(AuditUser user) {
        Set<String> roleKeys = splitRoleKeys(user.getRoleKeys());
        Set<String> permissions = SuperAdmin.matches(roleKeys)
                ? new HashSet<>(Collections.singletonList(SuperAdmin.WILDCARD_PERMISSION))
                : new LinkedHashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        return buildInfo(user, roleKeys, permissions);
    }

    private LoginInfoResponse buildInfo(AuditUser user, Set<String> roles, Set<String> permissions) {
        List<AuditMenu> menus = SuperAdmin.matches(roles)
                ? menuMapper.selectAllEnabledMenus()
                : menuMapper.selectMenusByUserId(user.getId());

        LoginInfoResponse response = new LoginInfoResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(TreeUtils.buildTree(menus));
        return response;
    }

    private Set<String> splitRoleKeys(String roleKeys) {
        if (roleKeys == null || roleKeys.trim().isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(roleKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
