package com.example.oauth.config;

import com.example.oauth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Collections;

/**
 * 授权服务器自身的 Web 安全配置
 * <p>
 * 本服务共有四条安全链，各管一段，顺序如下（数字为 {@code @Order}）：
 * <ol>
 *   <li>令牌端点的客户端认证链（{@link OAuth2TokenEndpointSecurityConfig}）</li>
 *   <li>授权端点的浏览器登录链（{@link OAuth2AuthorizationSecurityConfig}）</li>
 *   <li>第三方开放接口链（{@link OpenApiSecurityConfig}）：只认 OAuth2 令牌的 scope</li>
 *   <li><b>本链</b>：兜底。放行授权流程用到的剩余 {@code /oauth/**} 页面/数据端点
 *       （{@code /oauth/approval-info}、{@code /oauth/test-callback}），其余路径一律拒绝</li>
 * </ol>
 * <p>
 * 客户端管理（{@code /client/**}）不在这里 —— 它是后台管理功能，跑在 rbac-backend 上，
 * 由那边的登录令牌 + {@code system:client:*} 权限点 + 操作日志负责。
 * 因此本服务<b>不解析后台的登录令牌</b>，也<b>不加载 RBAC 权限</b>：它只签发/校验自己发出的
 * OAuth2 令牌，并校验用户身份（密码模式与授权码模式的表单登录）。
 * <p>
 * 这里同时提供 {@link AuthenticationManager} 与 {@link PasswordEncoder}：
 * OAuth2 的密码模式要用它校验 {@code sys_user} 表里的用户名密码。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 允许的前端源，逗号分隔；CORS 与非 GET 请求的 Origin 校验共用 */
    @Value("${rbac.cors.allowed-origins:http://localhost:5174,http://localhost:5177}")
    private String allowedOrigins;


    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // 隐藏 "User not found" 细节，统一提示
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(4)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // 授权流程用到的剩余端点：确认页数据、授权码回调联调端点。
                    // 授权页面端点由链 2 负责，令牌端点由链 1 负责。
                    .antMatchers("/oauth/**").permitAll()
                    // 扫码登录短链：二维码内容，必须允许手机匿名访问
                    .antMatchers("/q/**").permitAll()
                    // 企业微信域名校验文件（WW_verify_xxx.txt），必须匿名访问
                    .antMatchers("/WW_verify_*.txt").permitAll()
                    // 兜底：本服务只对外提供 OAuth2 端点与 /openapi/**，没有别的接口
                    .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint);
        return http.build();
    }

    private List<String> allowedOriginList() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOriginList());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
