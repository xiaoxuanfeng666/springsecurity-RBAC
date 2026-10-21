package com.example.oauth.config;

import com.example.oauth.oauth.MybatisClientDetailsService;
import com.example.oauth.oauth.SsoTokenServices;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一门户 SSO 的令牌服务装配
 * <p>
 * 之所以独立成一个配置类，而不是写在 {@link AuthorizationServerConfig} 里：后者既要注入
 * {@link SsoTokenServices}、又要定义它，构造器注入会形成「要创建配置类实例 → 需要它自己定义的 Bean
 * → 又需要配置类实例」的循环依赖，而 Spring Boot 2.6+ 默认禁止循环引用，会启动失败。
 * 这与 {@link JwtTokenConfig} 被拆出来的原因是同一个。
 *
 * <h3>为什么这里要把 {@code DefaultTokenServices} 的装配整份抄一遍</h3>
 * 库的 {@code AuthorizationServerEndpointsConfigurer} 只有在「没有自定义 tokenServices」时才会
 * 自己去 {@code createDefaultTokenServices()}。一旦我们调了 {@code endpoints.tokenServices(...)}，
 * 就得自己把 tokenStore / enhancer / clientDetailsService / refresh 支持 / 刷新时重新认证
 * <b>全部</b>装配齐 —— 少一样都会在运行期以难懂的方式坏掉（例如没设 enhancer 就签不出 JWT，
 * 没设 refresh 重新认证的 provider，刷新令牌会在 ProviderNotFoundException 上直接失败）。
 * 下面每一项都对应库 {@code createDefaultTokenServices()} + {@code addUserDetailsService()} 的一行。
 */
@Configuration
@RequiredArgsConstructor
public class SsoTokenConfig {

    private final TokenStore jwtTokenStore;
    private final JwtAccessTokenConverter jwtAccessTokenConverter;
    private final Oauth2TokenEnhancer oauth2TokenEnhancer;
    private final MybatisClientDetailsService clientDetailsService;
    private final UserDetailsServiceImpl userDetailsService;
    private final SsoSessionStore ssoSessionStore;

    /**
     * 令牌增强链，顺序有意义：先由业务增强器附加自定义声明，再交给 JWT 转换器签名成密文。
     * <p>
     * 提成 Bean 是为了让 {@link SsoTokenServices} 与 {@link AuthorizationServerConfig} 共用同一条链 ——
     * 两边各 new 一条也能跑，但将来改链的内容就会漏改一处。
     */
    @Bean
    public TokenEnhancerChain tokenEnhancerChain() {
        List<TokenEnhancer> delegates = new ArrayList<>();
        delegates.add(oauth2TokenEnhancer);
        delegates.add(jwtAccessTokenConverter);

        TokenEnhancerChain chain = new TokenEnhancerChain();
        chain.setTokenEnhancers(delegates);
        return chain;
    }

    @Bean
    public SsoTokenServices ssoTokenServices() {
        SsoTokenServices tokenServices = new SsoTokenServices(ssoSessionStore, jwtTokenStore);
        tokenServices.setTokenStore(jwtTokenStore);
        tokenServices.setTokenEnhancer(tokenEnhancerChain());
        tokenServices.setClientDetailsService(clientDetailsService);
        tokenServices.setSupportRefreshToken(true);
        // 刷新令牌一次性使用：每次刷新换发新值，旧的立即作废（见 SsoTokenServices#refreshAccessToken）
        tokenServices.setReuseRefreshToken(false);
        tokenServices.setAuthenticationManager(refreshAuthenticationManager());
        return tokenServices;
    }

    /**
     * 专供「刷新令牌时重新认证」用的管理器。
     * <p>
     * 对应库里的 {@code addUserDetailsService()}：刷新令牌的认证对象是从 JWT 声明重建的，
     * 用户信息可能已经过时，所以库会用 {@code PreAuthenticatedAuthenticationToken} 再走一遍
     * {@code UserDetailsService} 重新加载。这解释了为什么刷新出来的新令牌里仍有
     * {@code additionalInfo}（userId / nickName）—— 重新认证后 principal 是完整的 {@code LoginUser}，
     * 而不是一个光秃秃的用户名串。
     * <p>
     * 不能复用本系统用户登录用的那个 {@code AuthenticationManager}：那是 {@code DaoAuthenticationProvider}，
     * 只认 {@code UsernamePasswordAuthenticationToken}，喂给它 {@code PreAuthenticatedAuthenticationToken}
     * 会因找不到支持的 provider 而抛 {@code ProviderNotFoundException}。
     */
    private ProviderManager refreshAuthenticationManager() {
        PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
        provider.setPreAuthenticatedUserDetailsService(
                new UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken>(userDetailsService));
        return new ProviderManager(Arrays.<AuthenticationProvider> asList(provider));
    }
}
