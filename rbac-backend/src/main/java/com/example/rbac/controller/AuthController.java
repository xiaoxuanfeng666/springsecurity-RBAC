package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.dto.LoginRequest;
import com.example.rbac.dto.RefreshRequest;
import com.example.rbac.dto.TokenResponse;
import com.example.rbac.security.SecurityUtils;
import com.example.rbac.service.AuthService;
import lombok.RequiredArgsConstructor;
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
     * 获取当前用户信息（含权限与菜单树）
     */
    @GetMapping("/info")
    public Result<LoginInfoResponse> info() {
        return Result.success(authService.getInfo(SecurityUtils.getLoginUser()));
    }

    /**
     * 管理员强制某用户下线
     */
    @DeleteMapping("/kick/{userId}")
    public Result<Void> kickOut(@PathVariable Long userId) {
        authService.kickOut(userId);
        return Result.success("已强制下线", null);
    }
}
