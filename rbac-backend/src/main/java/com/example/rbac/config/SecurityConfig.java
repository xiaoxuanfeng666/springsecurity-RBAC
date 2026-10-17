package com.example.rbac.config;

import com.example.rbac.security.JwtAuthenticationFilter;
import com.example.rbac.security.PortalAuthenticationFilter;
import com.example.rbac.security.RestAccessDeniedHandler;
import com.example.rbac.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PortalAuthenticationFilter portalAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

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

    /**
     * 统一门户的接口链（OAuth2 令牌 + 无状态）
     * <p>
     * 为什么单独一条链：门户用的是授权服务签发的 <b>OAuth2 令牌</b>，与本服务自己签发的管理端登录令牌
     * 是完全不同的两套凭证。若混在同一条链上，就得让 {@code JwtAuthenticationFilter} 去分辨两种令牌，
     * 一不小心门户令牌就能反过来调管理端接口（或反之）。分成两条链、各自只认自己的令牌，边界才清楚：
     * <ul>
     *   <li>{@code /portal/token} —— 浏览器拿到授权码后换令牌。<b>必须放行</b>：此刻用户还没有门户令牌，
     *       而换令牌要带 client_secret，只能由服务端做。</li>
     *   <li>{@code /portal/sso-login} 与 {@code /portal/sso-login/{code}} —— 由后端拼接授权地址并 302。
     *       也放行：第一个是登录页发起的（那时没有令牌），第二个是一键登录的隐藏 iframe 导航的
     *       （iframe 带不上 Authorization 头）。两者都不返回地址本身，只发 302，
     *       所以 client_secret 不会流到前端。</li>
     *   <li>其余 {@code /portal/**} —— 需要门户令牌（身份由授权服务的 {@code /oauth/check_token} 认定，
     *       不加载任何 RBAC 权限点）。</li>
     * </ul>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
        http
                .requestMatchers()
                    .antMatchers("/portal/**")
                .and()
                .csrf().disable()
                .cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .antMatchers("/portal/token", "/portal/sso-login", "/portal/sso-login/*").permitAll()
                    .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                .and()
                .addFilterBefore(portalAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 本系统自身的接口链（JWT + 无状态）
     * <p>
     * 只处理 RBAC 业务接口与 {@code /auth/**}；门户接口由上面的 {@link #portalFilterChain} 负责，
     * OAuth2 授权服务器与第三方开放接口（{@code /openapi/**}）已拆到 rbac-oauth 服务。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .cors().and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .authorizeRequests()
                    .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .antMatchers("/auth/login", "/auth/refresh").permitAll()
                    .anyRequest().authenticated()
                .and()
                .exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
