package com.example.rbac.identity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支持的外部身份提供方目录
 * <p>
 * MVP 阶段企业微信已实现完整登录；钉钉/OIDC 先作为可绑定的提供方占位，
 * 后续在 rbac-oauth 里实现对应 IdentityProvider 即可启用登录。
 */
public final class ExternalIdentityProviders {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("wecom", "企业微信");
        LABELS.put("dingtalk", "钉钉");
        LABELS.put("oidc", "OIDC");
        LABELS.put("mock", "Mock（测试）");
    }

    private ExternalIdentityProviders() {
    }

    public static Map<String, String> labels() {
        return LABELS;
    }

    public static boolean supports(String provider) {
        return provider != null && LABELS.containsKey(provider);
    }

    public static String label(String provider) {
        return LABELS.getOrDefault(provider, provider);
    }
}
