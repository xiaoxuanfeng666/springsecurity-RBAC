package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.TokenResponse;
import com.example.rbac.entity.SysMenu;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mq.SsoKickPublisher;
import com.example.rbac.security.*;
import com.example.rbac.util.TreeUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务：登录、刷新 Token、登出、强制下线
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

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;
    private final PermissionSnapshotStore snapshotStore;
    private final SysOperationLogService logService;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SsoKickPublisher ssoKickPublisher;

    /**
     * 登录：签发 Access + Refresh Token，并写入 Redis 会话
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            // isEnabled() 为 false 时统一抛 DisabledException，需回查状态区分「停用」与「离职」
            throw new BusinessException(disabledResultCode(request.getUsername()));
        } catch (BadCredentialsException e) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        String sessionId = JwtTokenProvider.newSessionId();
        String username = loginUser.getUsername();

        String accessToken = jwtTokenProvider.createAccessToken(userId, username, sessionId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, username, sessionId);
        tokenStore.saveSession(userId, sessionId, refreshToken, jwtTokenProvider.getRefreshTokenExpireMillis());
        // 登录快照：把用户信息、权限与角色固化到会话上，后续请求不再访问数据库
        snapshotStore.save(sessionId,
                new PermissionSnapshot(loginUser.getUser(), loginUser.getPermissions(),
                        loginUser.getRoles()),
                jwtTokenProvider.getRefreshTokenExpireMillis());

        log.info("用户登录成功: {} (userId={})", username, userId);
        logService.record(MODULE_AUTH, AuditAction.LOGIN, userId, username, "登录成功");
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpireSeconds());
    }

    /**
     * 刷新 Token：用 Refresh Token 换取新的 Access Token，会话不变。
     * Access Token 过期后前端据此续期。
     */
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parse(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserId(claims);
        String sessionId = jwtTokenProvider.getSessionId(claims);

        // 会话已删除（登出 / 停用 / 离职 / 强制下线），旧刷新凭证一并失效
        if (!tokenStore.isSessionValid(userId, sessionId)) {
            throw new BusinessException(ResultCode.SESSION_INVALID);
        }
        String stored = tokenStore.getRefreshToken(sessionId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            tokenStore.removeSession(userId);
            throw new BusinessException(disabledResultCode(user.getUsername()));
        }

        String accessToken = jwtTokenProvider.createAccessToken(userId, user.getUsername(), sessionId);
        log.debug("刷新 Access Token: userId={}", userId);
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpireSeconds());
    }

    /**
     * 登出：删除当前会话
     */
    public void logout(LoginUser loginUser) {
        if (loginUser == null) {
            return;
        }
        tokenStore.removeSession(loginUser.getUser().getId());
        logService.record(MODULE_AUTH, AuditAction.LOGOUT,
                loginUser.getUser().getId(), loginUser.getUsername(), "主动登出");
        log.info("用户登出: {}", loginUser.getUsername());
    }

    /**
     * 获取当前登录用户信息（含权限与菜单树），前端据此渲染动态路由
     * <p>
     * 用户信息、权限与角色均取自会话快照（也就是服务端实际的鉴权依据），
     * 保证前端展示与后端判定一致；菜单树实时查库。
     * <p>
     * 超级管理员的权限不落库（见 {@link SuperAdmin}），菜单也就不能按 sys_role_menu 过滤，
     * 直接返回全部启用菜单 —— 这样新加菜单不需要给超级管理员补授权。
     */
    public LoginInfoResponse getInfo(LoginUser loginUser) {
        Long userId = loginUser.getUser().getId();
        // 快照内部是 Set，排序后返回保证接口输出稳定
        List<String> roles = loginUser.getRoles().stream().sorted().collect(Collectors.toList());
        List<String> permissions = loginUser.getPermissions().stream().sorted().collect(Collectors.toList());
        List<SysMenu> menus = TreeUtils.buildTree(SuperAdmin.matches(loginUser.getRoles())
                ? menuMapper.selectAllEnabledMenus()
                : menuMapper.selectMenusByUserId(userId));

        LoginInfoResponse response = new LoginInfoResponse();
        response.setUser(loginUser.getUser());
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);
        return response;
    }

    /**
     * 自助修改密码：校验原密码后更新，**保留当前会话**（不踢下线）。
     * <p>
     * 与管理员「重置密码」相对：重置是管理员代劳，会强制该用户下线；
     * 自助改密是本人操作，当前会话继续有效。
     */
    public void changeOwnPassword(LoginUser loginUser, String oldPassword, String newPassword) {
        Long userId = loginUser.getUser().getId();
        // 密码散列不在登录快照里（SysUser.password 标注了 @JsonIgnore），单独查一次；
        // 顺带保证校验的是「库里此刻的散列」，而不是会话建立时的旧值
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

        // 刻意不调用 tokenStore.removeSession，当前会话保持不变
        logService.record(MODULE_AUTH, AuditAction.CHANGE_PWD, userId,
                loginUser.getUsername(), "自助修改密码");
        log.info("用户自助修改密码: {}", loginUser.getUsername());
    }

    /**
     * 强制下线（需 system:user:edit）
     * <p>
     * 两个校验都放在删会话之前，因此被拒绝的请求不会写审计 ——
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
        tokenStore.removeSession(userId);
        // 通知授权服务删除 SSO 会话：该用户在各子系统的令牌立即失效
        ssoKickPublisher.publish(SsoKickPublisher.TYPE_KICK, null, userId,
                target.getUsername(), "被管理员强制下线");
        logService.record(MODULE_AUTH, AuditAction.KICK, userId, target.getUsername(), "强制下线");
        log.info("强制下线用户: {}", target.getUsername());
    }

    /**
     * 按用户名回查状态，区分「已离职」与「已停用」的登录失败提示
     */
    private ResultCode disabledResultCode(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user != null && user.getStatus() != null && user.getStatus() == STATUS_RESIGNED) {
            return ResultCode.RESIGNED;
        }
        return ResultCode.ACCOUNT_DISABLED;
    }
}
