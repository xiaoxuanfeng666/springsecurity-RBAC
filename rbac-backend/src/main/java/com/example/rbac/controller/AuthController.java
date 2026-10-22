package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.ChangePasswordRequest;
import com.example.rbac.dto.CodeLoginRequest;
import com.example.rbac.dto.ElevateRequest;
import com.example.rbac.dto.LoginInfoResponse;
import com.example.rbac.security.SecurityUtils;
import com.example.rbac.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发起统一 SSO 授权：拼好授权地址并 302 跳过去（未登录时前端就导航到这里）
     * <p>
     * 为什么这条跳转由后端做，而不是返回授权地址让前端自己跳：客户端配置（client_id、回调地址）
     * 与 state 都由服务端生成，前端只做一次导航，不必也不应接触这些参数。
     */
    @GetMapping("/sso-login")
    public void ssoLogin(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, authService.buildAuthorizeUrl(response));
    }

    /**
     * 统一 SSO 登录：用授权服务回调带回来的 code + state 换令牌并建立会话
     * <p>
     * state 必须与发起授权时下发的 HttpOnly Cookie 一致，且只能使用一次。
     * 登录成功后不返回任何令牌：令牌存进服务端 BFF 会话，浏览器只拿到 HttpOnly Cookie
     * 和渲染页面所需的用户信息。
     */
    @PostMapping("/code")
    public Result<LoginInfoResponse> loginByCode(@Valid @RequestBody CodeLoginRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse response) {
        return Result.success("登录成功",
                authService.loginByCode(request.getCode(), request.getState(), httpRequest, response));
    }

    /**
     * 登出：销毁授权服务的 SSO 会话与本系统的 BFF 会话，并记一条操作日志
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return Result.success();
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
