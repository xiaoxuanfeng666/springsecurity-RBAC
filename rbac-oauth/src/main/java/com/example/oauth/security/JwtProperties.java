package com.example.oauth.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 访问令牌的携带方式配置
 * <p>
 * 只声明第三方怎么把 OAuth2 访问令牌放进请求（{@code Authorization: Bearer <token>}），
 * 供 {@link com.example.oauth.oauth.OpenApiTokenAuthenticationFilter} 读取。
 * <p>
 * 本服务不解析后台的登录令牌，因此这里没有 {@code secret} —— 客户端管理已移回 rbac-backend，
 * 登录态的校验不再发生在本服务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 请求头名称 */
    private String header;

    /** Token 前缀 */
    private String tokenPrefix;
}
