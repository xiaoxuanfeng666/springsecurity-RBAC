package com.example.oauth.identity;

/**
 * 身份提供方 SPI
 * <p>
 * 新增钉钉 / OIDC 等登录方式时实现本接口即可，登录主流程（state、绑定、JIT、会话）
 * 由 {@code IdpLoginService} 统一处理。
 */
public interface IdentityProvider {

    /** wecom / mock / dingtalk ... */
    String provider();

    /** 是否已配置并可对外提供 */
    boolean isEnabled();

    /** 浏览器跳转的授权页地址（可以是企业微信扫码页，也可以是本系统的 mock 回调） */
    String buildAuthorizeUrl(String state);

    /** 用回调 code 换取外部身份 */
    ExternalIdentity authenticate(String code);
}
