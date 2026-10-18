package com.example.oauth.config;

import com.example.oauth.oauth.MybatisClientDetailsService;
import com.example.oauth.oauth.SsoTokenServices;
import com.example.oauth.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerEndpointsConfiguration;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

/**
 * OAuth2 授权服务器：供第三方系统接入本系统的登录（五种模式全部支持）
 * <p>
 * <b>不含 {@code @EnableAuthorizationServer}</b>：该注解会连带导入
 * {@code AuthorizationServerSecurityConfiguration}（一个 {@code WebSecurityConfigurerAdapter}），
 * 而 Spring Security 5.7 明确禁止它与 {@code SecurityFilterChain} 共用
 * （{@code WebSecurityConfiguration} 中的硬断言），本项目主链正是 {@code SecurityFilterChain} 风格。
 * 因此这里只导入端点半部分 {@link AuthorizationServerEndpointsConfiguration} ——
 * 授权端点、令牌端点与五种 TokenGranter 照常获得，仅客户端认证由本项目的安全链自行接管
 * （见 {@link OAuth2TokenEndpointSecurityConfig}）。
 * <p>
 * 客户端配置不经 {@code ClientDetailsServiceConfigurer} 注册，而是把
 * {@link MybatisClientDetailsService} 直接暴露为 Bean，由端点半部分按类型注入取用。
 * <p>
 * 五种授权模式均由此库原生实现，取决于客户端在 {@code clientdetails.grantTypes} 中的配置：
 * <ul>
 *   <li>authorization_code —— 授权码模式</li>
 *   <li>password —— 密码模式（用本系统的用户体系校验用户名密码）</li>
 *   <li>implicit —— 隐式模式</li>
 *   <li>client_credentials —— 客户端凭证模式</li>
 *   <li>refresh_token —— 刷新令牌模式</li>
 * </ul>
 */
@Configuration
@Import(AuthorizationServerEndpointsConfiguration.class)
@RequiredArgsConstructor
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenStore jwtTokenStore;
    private final JwtAccessTokenConverter jwtAccessTokenConverter;
    private final TokenEnhancerChain tokenEnhancerChain;
    private final SsoTokenServices ssoTokenServices;
    private final AuthorizationCodeServices authorizationCodeServices;

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) {

        endpoints
                .tokenStore(jwtTokenStore)
                .accessTokenConverter(jwtAccessTokenConverter)
                .tokenEnhancer(tokenEnhancerChain) //配置tokenEnhancer
                // 用带 SSO 会话校验的令牌服务替换库的默认实现：/oauth/check_token 会额外要求
                // 该用户的 SSO 会话仍然存在，令牌签发/刷新时维护这个会话 —— 统一登出的实现基础。
                // 装配细节见 SsoTokenConfig（一旦自行提供 tokenServices，库就不再代劳装配）。
                .tokenServices(ssoTokenServices)
                // 密码模式：用本系统的用户体系校验 username/password（同一个 sys_user 表）
                .authenticationManager(authenticationManager)
                // 刷新令牌模式需要
                .userDetailsService(userDetailsService)
                // 授权码存 Redis（oauth2:code:*，默认 5 分钟 TTL）：
                // 多节点共享、服务重启不丢，且换令牌时原子取出删除，天然一次性。
                // 装配见 RedisAuthorizationCodeConfig。
                .authorizationCodeServices(authorizationCodeServices)
                .reuseRefreshTokens(true)
                 .allowedTokenEndpointRequestMethods(HttpMethod.GET, HttpMethod.POST); //支持GET,POST请求
    }
}
