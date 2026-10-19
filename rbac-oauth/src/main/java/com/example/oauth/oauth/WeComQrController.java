package com.example.oauth.oauth;

import com.example.oauth.common.Result;
import com.example.oauth.identity.WeComQrLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 企业微信扫码登录：短链二维码 + 电脑端轮询
 */
@RestController
@RequiredArgsConstructor
public class WeComQrController {

    private final WeComQrLoginService qrLoginService;

    /**
     * 创建二维码票据，返回短链和有效期
     */
    @PostMapping("/oauth/wecom/qr")
    public Result<Map<String, Object>> create(@RequestBody(required = false) Map<String, String> body,
                                              HttpServletResponse response) {
        String returnTo = body == null ? null : body.get("returnTo");
        return Result.success(qrLoginService.create(returnTo, response));
    }

    /**
     * 短链入口（二维码内容）：跳转到企业微信授权页
     */
    @GetMapping("/q/{code}")
    public void shortLink(@PathVariable String code, HttpServletResponse response) {
        String target = qrLoginService.resolveAuthorizeUrl(code);
        if (target == null) {
            response.setStatus(HttpServletResponse.SC_GONE);
            qrLoginService.renderInvalidShortLink(response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, target);
    }

    /**
     * 电脑端轮询扫码状态
     */
    @GetMapping("/oauth/wecom/qr/status")
    public Result<Map<String, Object>> status(HttpServletRequest request) {
        return Result.success(qrLoginService.status(request));
    }

    /**
     * 电脑端完成登录
     */
    @PostMapping("/oauth/wecom/qr/complete")
    public Result<Map<String, String>> complete(@RequestBody(required = false) Map<String, String> body,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        String loginCode = body == null ? null : body.get("loginCode");
        return Result.success(qrLoginService.complete(loginCode, request, response));
    }
}
