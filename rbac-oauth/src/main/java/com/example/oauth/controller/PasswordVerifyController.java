package com.example.oauth.controller;

import com.example.oauth.common.Result;
import com.example.oauth.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 密码校验（供子系统做敏感操作的二次验证）
 * <p>
 * 只回答「用户名 + 密码是否正确」，<b>不签发令牌、不建立会话、不影响现有登录态</b> ——
 * 如果走 {@code grant_type=password}，会新建 SSO 会话并把用户当前的会话顶掉。
 * <p>
 * 该端点挂在令牌端点的客户端认证链上：调用方必须带 {@code client_id:client_secret}
 * （HTTP Basic），避免暴露成一个匿名的密码试探接口。使用者是已接入的子系统后端，
 * 它们在执行删除、授权等敏感操作前，请用户再输入一次密码。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PasswordVerifyController {

    private final AuthenticationManager authenticationManager;

    @PostMapping("/oauth/verify-password")
    public Result<Boolean> verify(@RequestParam String username, @RequestParam String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            log.info("密码二次验证通过: username={}", username);
            return Result.success(true);
        } catch (AuthenticationException e) {
            log.info("密码二次验证未通过: username={}, reason={}", username, e.getMessage());
            return Result.error(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
    }
}
