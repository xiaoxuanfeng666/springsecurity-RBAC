package com.example.oauth.identity;

import com.example.oauth.common.Result;
import com.example.oauth.dto.IdpBindRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Map;

/**
 * 通用外部身份登录入口
 * <p>
 * 企业微信走 {@code /oauth/idp/wecom/**}；mock 走 {@code /oauth/idp/mock/**}。
 */
@RestController
@RequestMapping("/oauth/idp")
@RequiredArgsConstructor
public class IdpController {

    private final IdpLoginService idpLoginService;

    @GetMapping("/{provider}/authorize")
    public void authorize(@PathVariable String provider,
                          @RequestParam(required = false) String returnTo,
                          HttpServletResponse response) {
        idpLoginService.authorize(provider, returnTo, response);
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable String provider,
                         @RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        idpLoginService.callback(provider, code, state, request, response);
    }

    /**
     * 自助绑定：外部身份未绑定时，用本地账号密码确认身份
     */
    @PostMapping("/bind")
    public Result<Map<String, String>> bind(@Valid @RequestBody IdpBindRequest body,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        return idpLoginService.bind(body, request, response);
    }
}
