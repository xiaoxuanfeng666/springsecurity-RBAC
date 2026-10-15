package com.example.rbac.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.provider.endpoint.AuthorizationEndpoint;

import javax.annotation.PostConstruct;

/**
 * 用自定义授权（确认）页面替换库自带的 {@code WhitelabelApprovalEndpoint}
 * <p>
 * 库里默认的确认页是那个极简的 "OAuth Approval" HTML（{@code /oauth/confirm_access}）。
 * 它由 {@code AuthorizationEndpoint#setUserApprovalPage} 决定：该方法的值会作为
 * Spring MVC 的视图名使用，返回 {@code ModelAndView(userApprovalPage, model)}。
 * 因此把它设成 {@code redirect:前端地址} 即可让浏览器 302 到我们的页面，
 * 默认那个端点虽然仍在，但不再会被用到。
 * <p>
 * 待授权的数据（clientId、scope、state 等）由 {@code AuthorizationEndpoint} 通过
 * {@code @SessionAttributes} 写进 HTTP 会话，前端页面经
 * {@code GET /oauth/approval-info} 读取（见 {@code OAuth2ApprovalInfoController}）。
 * <p>
 * 注意：只有<b>没有</b>被 {@code clientdetails.autoApproveScopes} 覆盖的 scope 才会走到确认页 ——
 * 客户端若配了全部自动放行，用户不会看到这个页面。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OAuth2ApprovalConfig {

    @Value("${oauth2.approval-page-url}")
    private String approvalPageUrl;

    private final AuthorizationEndpoint authorizationEndpoint;

    @PostConstruct
    public void useCustomApprovalPage() {
        authorizationEndpoint.setUserApprovalPage("redirect:" + approvalPageUrl);
        log.info("OAuth2 授权确认页已指向自定义页面: {}", approvalPageUrl);
    }
}
