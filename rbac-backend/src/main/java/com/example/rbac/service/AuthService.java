package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.config.OAuth2ClientProperties;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.entity.SysMenu;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mq.SsoKickPublisher;
import com.example.rbac.security.*;
import com.example.rbac.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 认证服务（BFF 模式）：登录授权完全走 rbac-oauth，本服务只负责识别令牌带来的用户与维护权限快照。
 * <p>
 * 本系统没有自己的登录页：未登录时前端导航到 {@code /auth/sso-login}，由后端 302 到授权页；
 * 用户在 rbac-oauth 完成认证后带着 code 回到前端，前端再交给 {@code /auth/code} 建立会话。
 * <p>
 * 令牌<b>只留在服务端</b>：写进 Redis 的 BFF 会话，浏览器只拿到一个 HttpOnly 的会话 Cookie。
 * access token 过期时由 {@code OAuth2TokenAuthenticationFilter} 用会话里的 refresh token 静默续期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户状态：1 在职 / 0 停用 / 2 离职 */
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_RESIGNED = 2;

    /** 操作日志模块名 */
    private static final String MODULE_AUTH = "认证";

    private final OAuth2TokenClient tokenClient;
    private final OAuth2ClientProperties oauth2Properties;
    private final OAuth2StateStore stateStore;
    private final UserDetailsServiceImpl userDetailsService;
    private final PermissionSnapshotStore snapshotStore;
    private final SysOperationLogService logService;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SsoKickPublisher ssoKickPublisher;
    private final JwtTokenVerifier jwtTokenVerifier;
    private final BffSessionStore sessionStore;
    private final BffSessionCookie sessionCookie;

    /** 会话绝对有效期（秒）：从登录时刻算起，到点必须重新授权 */
    @Value("${rbac.bff.session-max-ttl-seconds:604800}")
    private long sessionMaxTtlSeconds;

    /**
     * 授权码模式登录：用回调地址上的 code 换取令牌并建立服务端会话
     * <p>
     * 先消费 state 再换令牌：state 必须来自本次 {@code /auth/sso-login} 签发，
     * 且只能使用一次。这样重放回调地址（或伪造回调）在换取令牌之前就被拒绝。
     */
    public LoginInfoResponse loginByCode(String code, String state,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (!stateStore.consume(state, request, response)) {
            throw new BusinessException(ResultCode.STATE_INVALID);
        }
        Map<String, Object> token = tokenClient.authorizationCodeGrant(code);
        LoginInfoResponse info = establishSession(token, response);
        log.info("用户通过统一 SSO 登录成功: {} (userId={})", info.getUser().getUsername(), info.getUser().getId());
        logService.record(MODULE_AUTH, AuditAction.LOGIN,
                info.getUser().getId(), info.getUser().getUsername(), "登录成功");
        return info;
    }

    /**
     * 授权码模式的授权地址（由本系统**后端**拼装，并由后端直接发起跳转）
     * <p>
     * 地址里只有 {@code client_id / redirect_uri / scope / state}。按 OAuth2 标准，
     * {@code client_secret} 不进入浏览器地址栏：它只在本系统后端调 {@code /oauth/token}
     * 换令牌时通过 HTTP Basic 提交，因此不会落进浏览器历史、Referer 或访问日志。
     * <p>
     * state 在这里生成并写入 Redis，同时下发 HttpOnly Cookie 做浏览器绑定；前端只负责导航。
     * <p>
     * 浏览器访问的地址走 {@code oauth2.browser-authorize-base-url}（授权前端 5174，与登录页同源）；
     * 后端自己调令牌端点仍用 {@code serverUrl}（7071）。
     */
    public String buildAuthorizeUrl(HttpServletResponse response) {
        String base = StringUtils.hasText(oauth2Properties.getBrowserAuthorizeBaseUrl())
                ? oauth2Properties.getBrowserAuthorizeBaseUrl()
                : oauth2Properties.getServerUrl();
        String effectiveState = stateStore.issue(response);
        return UriComponentsBuilder.fromHttpUrl(base + "/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", oauth2Properties.getClientId())
                .queryParam("redirect_uri", oauth2Properties.getRedirectUri())
                .queryParam("scope", oauth2Properties.getScope())
                .queryParam("state", effectiveState)
                .build()
                .toUriString();
    }

    /**
     * 登出：先用会话里的 access token 让授权服务销毁 SSO 会话（本系统与所有子系统立即失效），
     * 再删本地会话、清 Cookie，并记一条操作日志。没有会话时也正常返回（登出是幂等的）。
     */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        String sessionId = sessionCookie.read(request);
        if (StringUtils.hasText(sessionId)) {
            BffSession session = sessionStore.get(sessionId);
            sessionStore.delete(sessionId);
            if (session != null && StringUtils.hasText(session.getAccessToken())) {
                tokenClient.ssoLogout(session.getAccessToken());
            }
        }
        sessionCookie.clear(response);
        if (loginUser != null) {
            logService.record(MODULE_AUTH, AuditAction.LOGOUT,
                    loginUser.getUser().getId(), loginUser.getUsername(), "主动登出");
            log.info("用户登出: {}", loginUser.getUsername());
        }
    }

    /**
     * 获取当前登录用户信息（含权限与菜单树），前端据此渲染动态路由
     * <p>
     * 用户信息、权限与角色均取自权限快照（也就是服务端实际的鉴权依据），
     * 保证前端展示与后端判定一致；菜单树实时查库。
     * <p>
     * 超级管理员的权限不落库（见 {@link SuperAdmin}），菜单也就不能按 sys_role_menu 过滤，
     * 直接返回全部启用菜单 —— 这样新加菜单不需要给超级管理员补授权。
     */
    public LoginInfoResponse getInfo(LoginUser loginUser) {
        return buildInfo(loginUser.getUser(), loginUser.getRoles(), loginUser.getPermissions());
    }

    /**
     * 自助修改密码：校验原密码后更新，**保留当前会话**（不踢下线）。
     * <p>
     * 与管理员「重置密码」相对：重置会强制该用户下线；自助改密是本人操作，当前会话继续有效。
     */
    public void changeOwnPassword(LoginUser loginUser, String oldPassword, String newPassword) {
        Long userId = loginUser.getUser().getId();
        // 密码散列不在快照里（SysUser.password 标注了 @JsonIgnore），单独查一次；
        // 顺带保证校验的是「库里此刻的散列」，而不是快照建立时的旧值
        SysUser current = userMapper.selectById(userId);
        if (current == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        String currentEncoded = current.getPassword();
        if (!passwordEncoder.matches(oldPassword, currentEncoded)) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        if (passwordEncoder.matches(newPassword, currentEncoded)) {
            throw new BusinessException(ResultCode.NEW_PASSWORD_SAME);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);

        logService.record(MODULE_AUTH, AuditAction.CHANGE_PWD, userId,
                loginUser.getUsername(), "自助修改密码");
        log.info("用户自助修改密码: {}", loginUser.getUsername());
    }

    /**
     * 强制下线（需 system:user:edit）
     * <p>
     * 两个校验都放在失效快照之前，因此被拒绝的请求不会写审计 ——
     * 避免把并未发生的下线记成一条成功记录。
     */
    public void kickOut(Long userId) {
        LoginUser operator = SecurityUtils.getLoginUser();
        if (operator != null && operator.getUser().getId().equals(userId)) {
            throw new BusinessException(ResultCode.KICK_SELF);
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        snapshotStore.invalidateUser(userId);
        // 通知授权服务删除 SSO 会话：该用户的令牌在本系统与各子系统立即失效
        ssoKickPublisher.publish(SsoKickPublisher.TYPE_KICK, null, userId,
                target.getUsername(), "被管理员强制下线");
        logService.record(MODULE_AUTH, AuditAction.KICK, userId, target.getUsername(), "强制下线");
        log.info("强制下线用户: {}", target.getUsername());
    }

    /**
     * 换到令牌后建立服务端会话，并把会话 id 写进 HttpOnly Cookie。
     * <p>
     * 令牌有效只说明「他是 RBAC 的合法用户」；还要确认账号仍在本系统且在岗。
     * 同时保证权限快照存在，避免第一个请求还在查库。
     */
    private LoginInfoResponse establishSession(Map<String, Object> token, HttpServletResponse response) {
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
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(disabledResultCode(user));
        }
        PermissionSnapshot snapshot = userDetailsService.getOrBuildSnapshot(username);
        if (snapshot == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }

        long now = System.currentTimeMillis();
        BffSession session = new BffSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setLoginTime(now);
        session.setExpireAt(now + sessionMaxTtlSeconds * 1000L);
        sessionStore.save(session);
        sessionCookie.write(response, session.getSessionId());

        return buildInfo(user, snapshot.getRoles(), snapshot.getPermissions());
    }

    private LoginInfoResponse buildInfo(SysUser user, Set<String> roles, Set<String> permissions) {
        // 快照内部是 Set，排序后返回保证接口输出稳定
        List<String> sortedRoles = roles.stream().sorted().collect(Collectors.toList());
        List<String> sortedPermissions = permissions.stream().sorted().collect(Collectors.toList());
        List<SysMenu> menus = TreeUtils.buildTree(SuperAdmin.matches(roles)
                ? menuMapper.selectAllEnabledMenus()
                : menuMapper.selectMenusByUserId(user.getId()));

        LoginInfoResponse response = new LoginInfoResponse();
        response.setUser(user);
        response.setRoles(sortedRoles);
        response.setPermissions(sortedPermissions);
        response.setMenus(menus);
        return response;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private ResultCode disabledResultCode(SysUser user) {
        if (user.getStatus() != null && user.getStatus() == STATUS_RESIGNED) {
            return ResultCode.RESIGNED;
        }
        return ResultCode.ACCOUNT_DISABLED;
    }
}
