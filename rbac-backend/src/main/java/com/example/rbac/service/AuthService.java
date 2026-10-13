package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.TokenResponse;
import com.example.rbac.entity.SysMenu;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.security.*;
import com.example.rbac.util.TreeUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

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

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

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

        log.info("用户登录成功: {} (userId={})", username, userId);
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
        log.info("用户登出: {}", loginUser.getUsername());
    }

    /**
     * 获取当前登录用户信息（含权限与菜单树），前端据此渲染动态路由
     */
    public LoginInfoResponse getInfo(LoginUser loginUser) {
        Long userId = loginUser.getUser().getId();
        // 实时刷新，确保权限变更后立即生效
        SysUser user = userMapper.selectById(userId);
        List<String> roles = userMapper.selectRoleKeysByUserId(userId);
        List<String> permissions = menuMapper.selectPermsByUserId(userId);
        List<SysMenu> menus = TreeUtils.buildTree(menuMapper.selectMenusByUserId(userId));

        LoginInfoResponse response = new LoginInfoResponse();
        response.setUser(user);
        response.setRoles(roles);
        response.setPermissions(permissions);
        response.setMenus(menus);
        return response;
    }

    /**
     * 强制下线（管理员操作）
     */
    public void kickOut(Long userId) {
        tokenStore.removeSession(userId);
        log.info("强制下线用户: userId={}", userId);
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
