package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.dto.CodeLoginRequest;
import com.example.order.dto.LoginInfoResponse;
import com.example.order.dto.PasswordLoginRequest;
import com.example.order.dto.RefreshRequest;
import com.example.order.dto.TokenResponse;
import com.example.order.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 登录授权接口
 * <p>
 * 本系统没有自己的登录页与用户表：这里的每个接口都是把请求转给 rbac-oauth，
 * 或把令牌带来的身份落到本地同步数据上。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 密码模式登录：转发给授权服务的 grant_type=password
     */
    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return Result.success("登录成功",
                authService.loginByPassword(request.getUsername(), request.getPassword()));
    }

    /**
     * 授权码模式登录：用授权服务回调带回来的 code 换令牌
     */
    @PostMapping("/code")
    public Result<TokenResponse> loginByCode(@Valid @RequestBody CodeLoginRequest request) {
        return Result.success("登录成功", authService.loginByCode(request.getCode()));
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    /**
     * 授权码模式的跳转地址，前端拿到后直接整页跳转
     */
    @GetMapping("/authorize-url")
    public Result<String> authorizeUrl(@RequestParam(required = false) String state) {
        return Result.success(authService.buildAuthorizeUrl(state));
    }

    /**
     * 当前用户信息：权限点与菜单树，前端据此渲染动态路由与按钮
     */
    @GetMapping("/info")
    public Result<LoginInfoResponse> info() {
        return Result.success(authService.getInfo());
    }
}
