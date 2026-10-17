package com.example.order.controller;

import com.example.order.common.Result;
import com.example.order.dto.CodeLoginRequest;
import com.example.order.dto.LoginInfoResponse;
import com.example.order.dto.PasswordLoginRequest;
import com.example.order.dto.RefreshRequest;
import com.example.order.dto.TokenResponse;
import com.example.order.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
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
     * 发起授权：拼接授权地址并 302 跳过去（未登录时前端就导航到这里）
     * <p>
     * 为什么这条跳转由后端做，而不是「返回授权地址让前端自己跳」：授权地址上必须带
     * {@code client_secret}（本项目对授权端点的定制要求）。作为接口数据返回，密钥就会落进
     * 前端代码、浏览器网络面板和前端日志；这里让前端只做一次导航，URL 由后端拼、302 由后端发，
     * 客户端标识与密钥全程只在服务端。
     */
    @GetMapping("/sso-login")
    public void ssoLogin(@RequestParam(required = false) String state,
                         HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, authService.buildAuthorizeUrl(state));
    }

    /**
     * 当前用户信息：权限点与菜单树，前端据此渲染动态路由与按钮
     */
    @GetMapping("/info")
    public Result<LoginInfoResponse> info() {
        return Result.success(authService.getInfo());
    }
}
