package com.example.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 与 rbac-oauth 授权服务对接的配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "oauth2")
public class OAuth2ClientProperties {

    /** 授权服务地址，如 http://localhost:7071（后端到后端调用用：换令牌、校验令牌） */
    private String serverUrl;

    /**
     * 浏览器访问授权端点用的地址，如 http://localhost:5174（授权前端，把 /oauth 代理到授权服务）。
     * <p>
     * 与 {@link #serverUrl} 分开：令牌端点与 check_token 是后端在调，直连 7071；
     * 授权端点是浏览器跳过去的，走 5174 —— 与登录页同源，会话 Cookie 才对得上。
     * 没配时退回 {@link #serverUrl}。
     */
    private String browserAuthorizeBaseUrl;

    /** 本系统在 RBAC 里登记的客户端标识 */
    private String clientId;

    /** 客户端密钥明文（与 rbac-backend 客户端管理页里设置的一致） */
    private String clientSecret;

    /** 授权码模式的回调地址，必须与 clientdetails.redirectUrl 登记的一致 */
    private String redirectUri;

    /** 申请授权的范围 */
    private String scope;
}
