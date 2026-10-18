package com.example.oauth.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录页可用的认证方式
 * <p>
 * 登录页（rbac-oauth-web）通过 {@code /oauth/providers} 代理获取，据此动态渲染登录按钮。
 */
@RestController
@RequiredArgsConstructor
public class OAuthProviderController {

    private final IdentityProviderRegistry registry;

    @GetMapping("/oauth/providers")
    public Map<String, Object> providers() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (IdentityProvider provider : registry.all()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enabled", provider.isEnabled());
            item.put("label", label(provider.provider()));
            result.put(provider.provider(), item);
        }
        return result;
    }

    private String label(String provider) {
        if (WeComIdentityProvider.PROVIDER.equalsIgnoreCase(provider)) {
            return "企业微信扫码登录";
        }
        if (MockIdentityProvider.PROVIDER.equalsIgnoreCase(provider)) {
            return "Mock 扫码登录（测试）";
        }
        return provider + " 登录";
    }
}
