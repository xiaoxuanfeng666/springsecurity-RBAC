package com.example.rbac.outbound;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 出站推送提供方注册表
 */
@Component
public class OutboundProviderRegistry {

    private final Map<String, OutboundDirectoryProvider> providers = new LinkedHashMap<>();

    public OutboundProviderRegistry(List<OutboundDirectoryProvider> providerList) {
        for (OutboundDirectoryProvider provider : providerList) {
            providers.put(provider.provider().toLowerCase(Locale.ROOT), provider);
        }
    }

    public OutboundDirectoryProvider get(String name) {
        return name == null ? null : providers.get(name.toLowerCase(Locale.ROOT));
    }
}
