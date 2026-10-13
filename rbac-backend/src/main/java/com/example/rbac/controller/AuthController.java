package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.ChangePasswordRequest;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.RefreshRequest;
import com.example.rbac.dto.TokenResponse;
import com.example.rbac.security.SecurityUtils;
import com.example.rbac.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录，返回 Access Token + Refresh Token
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    /**
     * 刷新 Token：Access Token 过期后前端用 Refresh Token 换取新的 Access Token
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    /**
     * 登出，删除会话
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(SecurityUtils.getLoginUser());
        return Result.success();
    }

    /**
     * 自助修改密码
     * <p>
     * 与管理员「重置密码」（`PUT /user/{id}/resetPassword`）相对：本人改密保留当前会话，
     * 不会被踢下线；管理员重置他人密码则强制其下线。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changeOwnPassword(SecurityUtils.getLoginUser(),
                request.getOldPassword(), request.getNewPassword());
        return Result.success("密码修改成功", null);
    }

    /**
     * 获取当前用户信息（含权限与菜单树）
     */
    @GetMapping("/info")
    public Result<LoginInfoResponse> info() {
        return Result.success(authService.getInfo(SecurityUtils.getLoginUser()));
    }

    /**
     * 强制某用户下线
     * <p>
     * 权限与「设置用户状态」保持一致：能停用用户的人本就能让用户下线，
     * 单独放宽或收紧没有意义。
     */
    @DeleteMapping("/kick/{userId}")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    public Result<Void> kickOut(@PathVariable Long userId) {
        authService.kickOut(userId);
        return Result.success("已强制下线", null);
    }
}
