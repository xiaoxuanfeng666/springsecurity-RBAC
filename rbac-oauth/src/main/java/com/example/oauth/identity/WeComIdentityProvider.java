package com.example.oauth.identity;

import com.example.oauth.config.WeComProperties;
import com.example.oauth.oauth.WeComClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 企业微信身份提供方（包装 {@link WeComClient}）
 */
@Component
@RequiredArgsConstructor
public class WeComIdentityProvider implements IdentityProvider {

    public static final String PROVIDER = "wecom";

    private final WeComClient weComClient;
    private final WeComProperties properties;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return weComClient.isEnabled();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        return weComClient.buildQrConnectUrl(state);
    }

    @Override
    public ExternalIdentity authenticate(String code) {
        String userId = weComClient.getUserId(code);
        return new ExternalIdentity(PROVIDER, properties.getCorpId(), userId,
                null, null, null, null, Collections.emptyList());
    }
}
