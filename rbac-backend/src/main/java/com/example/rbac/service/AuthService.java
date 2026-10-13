package com.example.rbac.service;

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
import com.example.rbac.util.MenuTreeUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务：登录、无感知续期、登出、强制下线
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;

    /**
     * 登录：签发 Access + Refresh，并将会话写入 Redis
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (DisabledException e) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        String sessionId = JwtTokenProvider.newSessionId();

        String accessToken = jwtTokenProvider.createAccessToken(userId, loginUser.getUsername(), sessionId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, sessionId);
        tokenStore.saveSession(userId, sessionId, refreshToken, jwtTokenProvider.getRefreshTokenExpireMillis());

        log.info("用户登录成功: {} (userId={})", loginUser.getUsername(), userId);
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpireSeconds());
    }

    /**
     * 刷新 Access Token（无感知续期的服务端实现）
     * <p>
     * 校验：refresh 签名有效 + 类型正确 + Redis 中会话仍有效 + refresh 与 Redis 记录一致 + 用户仍启用。
     * 通过后签发新 Access（sessionId 不变），并滑动延长会话有效期。
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

        // 会话已失效（离职/被踢下线/已登出）或 refresh 不匹配
        if (!tokenStore.isSessionValid(userId, sessionId)
                || !refreshToken.equals(tokenStore.getRefreshToken(sessionId))) {
            throw new BusinessException(ResultCode.SESSION_INVALID);
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            tokenStore.removeSession(userId);
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            tokenStore.removeSession(userId);
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getUsername(), sessionId);
        // 滑动续期：有活跃请求就延长会话
        tokenStore.renewSession(userId, sessionId, jwtTokenProvider.getRefreshTokenExpireMillis());
        return new TokenResponse(newAccessToken, refreshToken, jwtTokenProvider.getAccessTokenExpireSeconds());
    }

    /**
     * 登出：删除当前会话
     */
    public void logout(LoginUser loginUser) {
        if (loginUser == null) {
            return;
        }
        tokenStore.removeSession(loginUser.getUser().getId(), loginUser.getSessionId());
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
        List<SysMenu> menus = MenuTreeUtils.buildTree(menuMapper.selectMenusByUserId(userId));

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
}
