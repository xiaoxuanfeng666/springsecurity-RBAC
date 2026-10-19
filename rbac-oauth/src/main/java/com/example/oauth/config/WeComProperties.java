package com.example.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信扫码登录配置
 * <p>
 * secret 支持两种形式：
 * <ul>
 *   <li>明文（建议用环境变量注入，不要提交到仓库）；</li>
 *   <li>{@code ENC(...)}：与客户端密钥相同的 AES/GCM 密文，由 {@code ClientSecretCipher} 解密。</li>
 * </ul>
 * 生产环境应通过 Vault/KMS/密钥管理服务注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    private boolean enabled;

    /** 企业微信 CorpID */
    private String corpId;

    /** 应用 AgentId */
    private String agentId;

    /** 应用 Secret（或 ENC(...) 密文） */
    private String secret;

    /** 扫码授权页地址 */
    private String qrConnectUrl = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";

    /** 企业微信网页授权（snsapi_base）地址，用于手机扫码后的授权码流程 */
    private String oauth2AuthorizeUrl = "https://open.weixin.qq.com/connect/oauth2/authorize";

    /** 企业微信 API 网关 */
    private String apiBaseUrl = "https://qyapi.weixin.qq.com";

    /**
     * 回调地址：必须是企业微信后台登记过的、公网可达的 HTTPS 地址，
     * 例如 https://sso.example.com/oauth/wecom/callback（网关转发到 rbac-oauth）
     */
    private String callbackUrl;
}
