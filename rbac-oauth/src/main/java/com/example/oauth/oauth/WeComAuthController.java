package com.example.oauth.oauth;

import com.example.oauth.identity.IdpLoginService;
import com.example.oauth.identity.WeComIdentityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 企业微信登录的兼容入口（等价于 {@code /oauth/idp/wecom/**}）
 */
@RestController
@RequestMapping("/oauth/wecom")
@RequiredArgsConstructor
public class WeComAuthController {

    private final IdpLoginService idpLoginService;

    @GetMapping("/authorize")
    public void authorize(@RequestParam(required = false) String returnTo,
                          HttpServletResponse response) {
        idpLoginService.authorize(WeComIdentityProvider.PROVIDER, returnTo, response);
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        idpLoginService.callback(WeComIdentityProvider.PROVIDER, code, state, request, response);
    }
}
