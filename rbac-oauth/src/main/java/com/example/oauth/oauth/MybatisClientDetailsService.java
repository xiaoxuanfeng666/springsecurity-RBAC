package com.example.oauth.oauth;

import com.example.oauth.entity.ClientDetails;
import com.example.oauth.mapper.ClientDetailsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从 {@code clientdetails} 表加载 OAuth2 客户端
 * <p>
 * 该表沿用 Spring Security OAuth 的约定（主键 appId、列名驼峰），与本库的 {@link BaseClientDetails}
 * 字段一一对应，因此「客户端管理」页面维护的数据可直接被授权服务器读取。
 * <p>
 * 授权模式（{@code grantTypes}）取值为 OAuth2 标准的逗号分隔字符串，五种模式全部受支持：
 * {@code authorization_code} / {@code password} / {@code implicit} / {@code client_credentials} / {@code refresh_token}。
 * 客户端密钥按 AES 密文存储，由令牌端点安全链上的 {@code PasswordEncoder} 解密比对（见
 * OAuth2TokenEndpointSecurityConfig）。
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class MybatisClientDetailsService implements ClientDetailsService {

    private final ClientDetailsMapper clientDetailsMapper;
    private final ObjectMapper objectMapper;
    private final ClientSecretCipher clientSecretCipher;
    private final ClientDetailsCacheStore cacheStore;

    @Override
    public BaseClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
        // 先读 Redis：令牌端点每次请求都会走这里，而客户端配置极少变化
        ClientDetails cached = cacheStore.get(clientId);
        if (cached != null) {
            // 缓存只写入通过校验的客户端（见下），命中时无需重复解密
            return toClientDetails(cached);
        }

        ClientDetails client = clientDetailsMapper.selectById(clientId);
        if (client == null) {
            throw new ClientRegistrationException("客户端不存在: " + clientId);
        }
        // 客户端密钥不允许为空：空密钥会被当成公开客户端，任何人都能换取令牌。
        // 在此拦下并给出明确原因，比让它以 invalid_client 的形式失败更易排查。
        if (!StringUtils.hasText(client.getAppSecret())) {
            throw new ClientRegistrationException("客户端未配置密钥，请先在「客户端管理」中设置: " + clientId);
        }
        // 空 scope 不是「最严格」而是「完全不受限」：授权服务器的范围校验在客户端 scope
        // 为空时会跳过逐项比对（见 DefaultOAuth2RequestValidator），任何 scope 都能申请到
        if (!StringUtils.hasText(client.getScope())) {
            throw new ClientRegistrationException("客户端未配置授权范围(scope)，请先在「客户端管理」中设置: " + clientId);
        }
        // 密钥在库中是 AES 密文。解不开说明它不是当前密钥加密的（例如更换过
        // oauth2.client-secret-key，或遗留的旧格式数据），此时认证必然失败，
        // 提前给出可操作的原因，而不是让用户看到含糊的「用户名或密码错误」。
        if (clientSecretCipher.decrypt(client.getAppSecret()) == null) {
            throw new ClientRegistrationException(
                    "客户端密钥无法解密，请重新设置密钥（配置项 oauth2.client-secret-key 变更后需重设）: " + clientId);
        }
        // 只缓存通过校验的客户端；管理端改/删后会经 MQ 删除该键
        cacheStore.save(client);
        return toClientDetails(client);
    }

    private BaseClientDetails toClientDetails(ClientDetails client) {
        BaseClientDetails details = new BaseClientDetails();
        details.setClientId(client.getAppId());
        details.setClientSecret(client.getAppSecret());
        details.setScope(split(client.getScope()));
        details.setResourceIds(split(client.getResourceIds()));
        details.setAuthorizedGrantTypes(split(client.getGrantTypes()));
        details.setRegisteredRedirectUri(split(client.getRedirectUrl()));
        details.setAuthorities(toAuthorities(client.getAuthorities()));
        details.setAccessTokenValiditySeconds(client.getAccessTokenValidity());
        details.setRefreshTokenValiditySeconds(client.getRefreshTokenValidity());
        details.setAutoApproveScopes(split(client.getAutoApproveScopes()));
        details.setAdditionalInformation(parseAdditionalInformation(client.getAdditionalInformation()));
        return details;
    }

    /** 逗号分隔 → 去空白的有序集合 */
    private static Set<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptySet();
        }
        List<String> values = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return new LinkedHashSet<>(values);
    }

    private static Set<GrantedAuthority> toAuthorities(String csv) {
        return split(csv).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * additionalInformation 在库中是 JSON 字符串；解析失败仅告警并忽略，不影响客户端加载
     */
    private java.util.Map<String, Object> parseAdditionalInformation(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("客户端扩展信息解析失败，已忽略: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
