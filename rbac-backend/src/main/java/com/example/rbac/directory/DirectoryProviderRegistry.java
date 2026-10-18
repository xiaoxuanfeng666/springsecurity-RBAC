package com.example.rbac.directory;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通讯录提供方注册表
 */
@Component
public class DirectoryProviderRegistry {

    private final Map<String, DirectoryProvider> providers = new LinkedHashMap<>();

    public DirectoryProviderRegistry(List<DirectoryProvider> providerList) {
        for (DirectoryProvider provider : providerList) {
            providers.put(provider.provider().toLowerCase(Locale.ROOT), provider);
        }
    }

    public DirectoryProvider get(String name) {
        return name == null ? null : providers.get(name.toLowerCase(Locale.ROOT));
    }
}
