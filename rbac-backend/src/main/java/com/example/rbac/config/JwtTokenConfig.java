package com.example.rbac.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * 授权服务器的令牌存储与签名配置
 * <p>
 * 之所以独立成一个配置类，而不是放在 {@link AuthorizationServerConfig} 内部：
 * 后者需要注入这两个 Bean，若定义与注入同处一个类，构造器注入会形成
 * 「要创建配置类实例 → 需要它自己定义的 Bean → 又需要配置类实例」的循环依赖，
 * 而 Spring Boot 2.6+ 默认禁止循环引用（{@code allow-circular-references=false}），会启动失败。
 */
@Configuration
@RequiredArgsConstructor
public class JwtTokenConfig {

    private final JwtCAProperties jwtCAProperties;

    /**
     * JWT 令牌转换器：access token 与 refresh token 都是自包含的 JWT（RSA 签名），
     * 因此无需 oauth_access_token / oauth_refresh_token 等令牌表。
     * <p>
     * 密钥对启动时随机生成、仅存内存 —— 重启后旧令牌无法验签即失效，
     * {@code /oauth/token_key} 暴露的公钥也随之更新。生产环境若需重启不失效，
     * 应改用固定密钥（KeyStoreKeyFactory）或多实例共享的密钥源。
     */
    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter() {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setKeyPair(generateRsaKeyPair());
        return converter;
    }

    @Bean
    public TokenStore jwtTokenStore(JwtAccessTokenConverter jwtAccessTokenConverter) {
        return new JwtTokenStore(jwtAccessTokenConverter);
    }

    private  KeyPair generateRsaKeyPair() {
        try {
//            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
//            keyPairGenerator.initialize(2048);
//            return keyPairGenerator.generateKeyPair();

            KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(
                    new ClassPathResource(jwtCAProperties.getKeyPairName()), jwtCAProperties.getKeyPairSecret().toCharArray());
            return keyStoreKeyFactory.getKeyPair(jwtCAProperties.getKeyPairAlias(), jwtCAProperties.getKeyPairStoreSecret().toCharArray());
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }
}
