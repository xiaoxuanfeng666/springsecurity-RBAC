package com.example.oauth.identity;

import com.example.oauth.config.IdpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mock 身份提供方（仅开发/测试）
 * <p>
 * 不访问任何外部系统：authorize 直接跳回本服务的 callback，用固定身份完成登录，
 * 便于验证自助绑定、JIT、多 IdP 等链路。
 */
@Component
@RequiredArgsConstructor
public class MockIdentityProvider implements IdentityProvider {

    public static final String PROVIDER = "mock";

    private final IdpProperties idpProperties;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return idpProperties.isMockEnabled();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return "/oauth/idp/mock/callback?code=mock-code&state=" + state;
    }

    @Override
    public ExternalIdentity authenticate(String code) {
        IdpProperties.Mock mock = idpProperties.getMock();
        ExternalIdentity identity = new ExternalIdentity();
        identity.setProvider(PROVIDER);
        identity.setCorpId(mock.getCorpId());
        identity.setExternalUserId(mock.getExternalUserId());
        identity.setDisplayName(mock.getDisplayName());
        identity.setMobile(mock.getMobile());
        identity.setEmail(mock.getEmail());
        identity.setDepartmentIds(mock.getDepartmentIds());
        return identity;
    }
}
