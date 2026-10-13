package com.example.rbac.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥 */
    private String secret;

    /** Access Token 有效期（毫秒） */
    private Long accessTokenExpire;

    /** Refresh Token 有效期（毫秒） */
    private Long refreshTokenExpire;

    /** 请求头名称 */
    private String header;

    /** Token 前缀 */
    private String tokenPrefix;
}
