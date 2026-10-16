package com.example.oauth.oauth;

import com.example.oauth.common.Result;
import org.springframework.security.oauth2.provider.AuthorizationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 待授权请求的查询接口，供自定义授权（确认）页面渲染用
 * <p>
 * 授权端点（{@code AuthorizationEndpoint}）在需要用户确认时，会把
 * {@code AuthorizationRequest} 以 {@code @SessionAttributes} 写进 HTTP 会话
 * （属性名 {@code authorizationRequest}）。确认页（rbac-oauth-web 的 /approve）拿不到会话，
 * 故由本接口读出来转成 JSON。
 * <p>
 * 路径放在 {@code /oauth/**} 下，因此无需登录即可访问（管理端登录态链对 {@code /oauth/**} 放行），
 * 与授权流程本身的「未登录先跳登录页」并不冲突 —— 能走到确认页说明用户已经登录过。
 */
@RestController
public class OAuth2ApprovalInfoController {

    /** 与 AuthorizationEndpoint.AUTHORIZATION_REQUEST_ATTR_NAME 一致；该常量是包级私有，只能写字面量 */
    private static final String AUTHORIZATION_REQUEST_ATTR = "authorizationRequest";

    @GetMapping("/oauth/approval-info")
    public Result<Map<String, Object>> approvalInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(AUTHORIZATION_REQUEST_ATTR);
        if (!(attribute instanceof AuthorizationRequest)) {
            return Result.error("当前没有待授权的请求，请重新发起授权");
        }

        AuthorizationRequest authorizationRequest = (AuthorizationRequest) attribute;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientId", authorizationRequest.getClientId());
        data.put("scopes", authorizationRequest.getScope() == null
                ? Collections.emptyList() : new ArrayList<>(authorizationRequest.getScope()));
        data.put("state", authorizationRequest.getState());
        data.put("redirectUri", authorizationRequest.getRedirectUri());
        data.put("responseTypes", authorizationRequest.getResponseTypes());
        return Result.success(data);
    }
}
