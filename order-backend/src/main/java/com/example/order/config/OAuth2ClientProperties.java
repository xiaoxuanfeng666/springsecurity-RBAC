package com.example.order.config;

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

    /** 授权服务地址，如 http://localhost:7071 */
    private String serverUrl;

    /** 本系统在 RBAC 里登记的客户端标识 */
    private String clientId;

    /** 客户端密钥明文（与 rbac-backend 客户端管理页里设置的一致） */
    private String clientSecret;

    /** 授权码模式的回调地址，必须与 clientdetails.redirectUrl 登记的一致 */
    private String redirectUri;

    /** 申请授权的范围 */
    private String scope;
}
