package com.example.oauth.identity;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 身份提供方注册表
 */
@Component
public class IdentityProviderRegistry {

    private final Map<String, IdentityProvider> providers = new LinkedHashMap<>();

    public IdentityProviderRegistry(List<IdentityProvider> providerList) {
        for (IdentityProvider provider : providerList) {
            providers.put(provider.provider().toLowerCase(Locale.ROOT), provider);
        }
    }

    public IdentityProvider get(String name) {
        return name == null ? null : providers.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<IdentityProvider> all() {
        return providers.values();
    }
}
