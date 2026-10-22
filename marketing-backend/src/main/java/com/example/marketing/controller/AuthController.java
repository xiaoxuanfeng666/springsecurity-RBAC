package com.example.marketing.controller;

import com.example.marketing.common.Result;
import com.example.marketing.dto.CodeLoginRequest;
import com.example.marketing.dto.ElevateRequest;
import com.example.marketing.dto.LoginInfoResponse;
import com.example.marketing.dto.PasswordLoginRequest;
import com.example.marketing.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

/**
 * 登录授权接口
 * <p>
 * 本系统没有自己的登录页与用户表：这里的每个接口都是把请求转给 rbac-oauth，
 * 或把令牌带来的身份落到本地同步数据上。
 * <p>
 * 登录成功后<b>不返回任何令牌</b>：令牌存进服务端 BFF 会话，浏览器只拿到 HttpOnly Cookie
 * 和渲染页面所需的用户信息。续期与登出也都在服务端完成。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 密码模式登录：转发给授权服务的 grant_type=password（供第三方接入与联调）
     */
    @PostMapping("/login")
    public Result<LoginInfoResponse> login(@Valid @RequestBody PasswordLoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse response) {
        return Result.success("登录成功",
                authService.loginByPassword(request.getUsername(), request.getPassword(), httpRequest, response));
    }

    /**
     * 授权码模式登录：用授权服务回调带回来的 code + state 换令牌并建立会话
     */
    @PostMapping("/code")
    public Result<LoginInfoResponse> loginByCode(@Valid @RequestBody CodeLoginRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse response) {
        return Result.success("登录成功",
                authService.loginByCode(request.getCode(), request.getState(), httpRequest, response));
    }

    /**
     * 登出：销毁授权服务的 SSO 会话与本系统的 BFF 会话
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return Result.success("已登出", null);
    }


    /**
     * 敏感操作二次验证：重新输入登录密码
     */
    @PostMapping("/elevate")
    public Result<Void> elevate(@Valid @RequestBody ElevateRequest request,
                                HttpServletRequest httpRequest) {
        authService.elevate(request.getPassword(), httpRequest);
        return Result.success("验证通过", null);
    }

    /**
     * 发起授权：拼接授权地址并 302 跳过去（未登录时前端就导航到这里）
     * <p>
     * 为什么这条跳转由后端做：客户端配置（client_id、回调地址）与 state 都由服务端生成，
     * 前端只做一次导航，不必也不应接触这些参数。client_secret 只用于后端调令牌端点。
     */
    @GetMapping("/sso-login")
    public void ssoLogin(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, authService.buildAuthorizeUrl(response));
    }

    /**
     * 当前用户信息：权限点与菜单树，前端据此渲染动态路由与按钮
     */
    @GetMapping("/info")
    public Result<LoginInfoResponse> info() {
        return Result.success(authService.getInfo());
    }
}
