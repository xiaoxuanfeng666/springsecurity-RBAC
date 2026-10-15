package com.example.rbac.config;

import com.example.rbac.oauth.ClientSecretCipher;
import com.example.rbac.oauth.MybatisClientDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.client.ClientCredentialsTokenEndpointFilter;
import org.springframework.security.oauth2.provider.client.ClientDetailsUserDetailsService;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * OAuth2 令牌端点的客户端认证链
 * <p>
 * 传统 spring-security-oauth2 原本由 {@code @EnableAuthorizationServer} 自带的
 * {@code AuthorizationServerSecurityConfiguration}（一个 {@code WebSecurityConfigurerAdapter}）负责这层，
 * 但它与 Spring Security 5.7 的 {@code SecurityFilterChain} 风格互斥，故这里用安全链等价重建：
 * <ul>
 *   <li>HTTP Basic 认证客户端（curl 用 {@code -u clientId:secret}）</li>
 *   <li>表单参数提交 client_id/client_secret 也接受（库的 {@code allowFormAuthenticationForClients} 等价物）</li>
 *   <li>客户端密钥存储在库中是 AES 密文，校验时解密比对；密钥不允许为空</li>
 *   <li>{@code /oauth/token_key}、{@code /oauth/check_token} 对外开放，便于第三方验签 / 远程校验令牌</li>
 * </ul>
 * 客户端认证用的是独立构造的 {@code AuthenticationManager}（只认客户端，不认 sys_user），
 * 避免与本系统用户认证的管理器相互干扰。
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2TokenEndpointSecurityConfig {

    private static final String REALM = "rbac";

    private final MybatisClientDetailsService clientDetailsService;
    private final ClientSecretCipher clientSecretCipher;

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2TokenEndpointFilterChain(HttpSecurity http) throws Exception {
        AuthenticationManager clientAuthenticationManager = clientAuthenticationManager();

        // 用库自带的 OAuth2 入口点而非 BasicAuthenticationEntryPoint：
        // 后者以 sendError 结束，会触发 /error 转发（而 /error 归主 JWT 链管），
        // 结果第三方拿到的是本系统风格的 401 JSON，而非 OAuth2 标准的 invalid_client。
        // OAuth2AuthenticationEntryPoint 直接写出响应体，同时保留 WWW-Authenticate: Basic 头。
        OAuth2AuthenticationEntryPoint entryPoint = new OAuth2AuthenticationEntryPoint();
        entryPoint.setRealmName(REALM);

        ClientCredentialsTokenEndpointFilter formClientFilter =
                new ClientCredentialsTokenEndpointFilter("/oauth/token");
        formClientFilter.setAuthenticationManager(clientAuthenticationManager);
        formClientFilter.setAuthenticationEntryPoint(entryPoint);

        http
                .requestMatchers()
                    .antMatchers("/oauth/token", "/oauth/token_key", "/oauth/check_token")
                .and()
                .authorizeRequests()
                    // 令牌端点必须已通过客户端认证
                    .antMatchers("/oauth/token").fullyAuthenticated()
                    // 公钥与令牌校验对第三方开放
                    .antMatchers("/oauth/token_key", "/oauth/check_token").permitAll()
                .and()
                // 表单参数的客户端认证需先于 Basic，二者都在授权校验之前
                .addFilterBefore(new BasicAuthenticationFilter(clientAuthenticationManager, entryPoint),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(formClientFilter, BasicAuthenticationFilter.class)
                .exceptionHandling()
                    .authenticationEntryPoint(entryPoint)
                .and()
                // 与库的默认一致：令牌端点不建立会话
                .securityContext()
                    .securityContextRepository(new NullSecurityContextRepository())
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        return http.build();
    }

    private AuthenticationManager clientAuthenticationManager() {
        PasswordEncoder secretEncoder = clientSecretEncoder();

        ClientDetailsUserDetailsService userDetailsService =
                new ClientDetailsUserDetailsService(clientDetailsService);
        userDetailsService.setPasswordEncoder(secretEncoder);

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(secretEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /**
     * 客户端密钥的比对器：库中存的是 AES 密文，解密后与请求携带的密钥做常量时间比较。
     * <p>
     * 刻意不复用容器里的 {@code PasswordEncoder}（那是给用户密码用的 BCrypt），
     * 也不把它注册成 Bean —— 否则 {@code PasswordEncoder} 类型会出现两个候选，
     * 破坏本系统用户登录处按类型注入的装配。
     */
    private PasswordEncoder clientSecretEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return clientSecretCipher.encrypt(rawPassword.toString());
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encryptedSecret) {
                String plain = clientSecretCipher.decrypt(encryptedSecret);
                return plain != null && MessageDigest.isEqual(
                        plain.getBytes(StandardCharsets.UTF_8),
                        rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
