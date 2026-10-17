package com.example.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一门户（5177）与授权服务的对接配置
 * <p>
 * 门户本身没有用户体系，它的登录也走 rbac-oauth 的授权码模式，
 * 只是客户端凭证换成了 {@code portal} 这个客户端（见 sql/upgrade-2026-10-16-sso.sql）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "oauth2.portal")
public class PortalProperties {

    /** 授权服务地址，如 http://localhost:7071 */
    private String serverUrl;

    /** 门户在 RBAC 里登记的客户端标识 */
    private String clientId;

    /** 门户客户端密钥明文 */
    private String clientSecret;

    /** 门户自己的授权码回调地址（门户前端页面） */
    private String redirectUri;

    /** 门户向授权服务申请的授权范围 */
    private String scope = "read";

    /**
     * 一键登录子系统时替子系统申请的授权范围。
     * <p>
     * 子系统的 {@code application.yml} 里申请的也是 read，且两个子系统客户端的
     * {@code autoApproveScopes} 都已放行 read（见增量脚本），所以这一步不会弹确认页。
     */
    private String subsystemScope = "read";
}
