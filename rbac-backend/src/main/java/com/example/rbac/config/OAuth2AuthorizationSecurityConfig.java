package com.example.rbac.config;

import com.example.rbac.oauth.AuthorizeClientSecretFilter;
import com.example.rbac.oauth.ClientSecretCipher;
import com.example.rbac.oauth.MybatisClientDetailsService;
import com.example.rbac.oauth.OAuth2LoginEntryPoint;
import com.example.rbac.security.RedisRequestCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

/**
 * 授权端点的浏览器登录链（授权码模式与隐式模式需要用户先登录）
 * <p>
 * {@code /oauth/authorize}、{@code /oauth/confirm_access}、{@code /oauth/error} 由授权服务器以
 * MVC 控制器形式提供（{@code AuthorizationEndpoint} 等），不属于令牌端点链的匹配范围；
 * 若不单独接管就会落到本系统的 JWT 链上 —— 而浏览器跳转不带 Bearer 令牌，必然 401。
 * 因此这里用一条独立的安全链，为其启用基于表单登录的会话认证。
 * <p>
 * 隔离性：{@code requestMatchers} 只匹配下面这几个 {@code /oauth/**} 页面端点，且本链排在
 * JWT 链（{@code @Order(3)}）之前，因此不影响 {@code /auth/**} 与其余业务接口。
 * <p>
 * 多节点：本链的登录态是 HTTP 会话（已由 Spring Session 存到 Redis），
 * 「登录后跳回授权页」的地址由 {@link RedisRequestCache} 存 Redis —— 两者都不再依赖节点亲和。
 * <p>
 * 另按项目要求，本链在授权端点上校验 {@code client_secret}（见 {@link AuthorizeClientSecretFilter}）。
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2AuthorizationSecurityConfig {

    /**
     * 授权流程的登录页（前端页面），形如 {@code http://localhost:5173/login}。
     * 必须是浏览器可达的绝对地址：登录页在前端，若只想当然地给相对路径 {@code /login}，
     * 从后端源发起授权时会跳到后端自己的 /login（那里没有页面），最终以 401 收场。
     */
    @Value("${oauth2.login-page-url}")
    private String loginPageUrl;

    private final StringRedisTemplate stringRedisTemplate;
    private final MybatisClientDetailsService clientDetailsService;
    private final ClientSecretCipher clientSecretCipher;
    private final ObjectMapper objectMapper;

    @Bean
    @Order(2)
    public SecurityFilterChain oauth2AuthorizationFilterChain(HttpSecurity http) throws Exception {
        // 跳回地址的存取必须用同一个实例：保存发生在 ExceptionTranslationFilter（用链上配置的
        // RequestCache），读取发生在下面这个成功处理器。成功处理器默认会自带一个
        // HttpSessionRequestCache，若不给它显式设置，两边对不上就会读不到、只能退回默认首页。
        RedisRequestCache requestCache = new RedisRequestCache(stringRedisTemplate);

        // 登录成功后跳回此前被缓存的 /oauth/authorize
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setRequestCache(requestCache);
        // 缓存里存的就是相对地址（路径 + 查询串），原样发出即可 —— 浏览器按当前源解析，
        // 因此不会把用户带离前端源。切勿在此再「绝对转相对」：地址的查询串里嵌套了
        // redirect_uri=http://... 这类内容，按 :// 判断会被误伤截断。
        successHandler.setRedirectStrategy((request, response, url) -> {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader(HttpHeaders.LOCATION, url);
        });

        http
                .requestMatchers()
                    .antMatchers("/oauth/authorize", "/oauth/confirm_access", "/oauth/error", "/oauth/login")
                .and()
                .authorizeRequests()
                    .antMatchers("/oauth/login").permitAll()
                    .anyRequest().authenticated()
                .and()
                // 按项目要求校验授权请求上的 client_secret（标准 OAuth2 不做这一步，
                // 详见 AuthorizeClientSecretFilter 的类注释与其中的风险提示）。
                // 放在表单登录之前：密钥不对就没必要先让用户去登录。
                .addFilterBefore(
                        new AuthorizeClientSecretFilter(clientDetailsService, clientSecretCipher, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .formLogin()
                    // 登录页复用前端已有的 /login；表单以原生 POST 提交到 /oauth/login，
                    // 成功后会跳回此前被缓存的 /oauth/authorize。
                    // 这里的 loginPage 只声明路径，真正的跳转目标由下面的入口点按配置指定
                    .loginPage("/login")
                    .loginProcessingUrl("/oauth/login")
                    .successHandler(successHandler)
                    // 失败时保留 oauth2 标记，前端才能留在「OAuth2 授权登录」模式下提示错误
                    .failureUrl(OAuth2LoginEntryPoint.withOAuthFlag(loginPageUrl) + "&error=1")
                    .permitAll()
                .and()
                // 显式指定入口点，覆盖 formLogin 的默认跳转：既带上 oauth2 标记供前端区分，
                // 也保证跳到配置好的前端绝对地址
                .exceptionHandling()
                    .authenticationEntryPoint(new OAuth2LoginEntryPoint(loginPageUrl))
                .and()
                // 「登录后跳回授权页」的目标地址存 Redis，而非默认的会话 —— 多节点下不依赖节点亲和
                .requestCache()
                    .requestCache(requestCache)
                .and()
                // 与主链保持一致：本项目为前后端分离，跨域与 CSRF 由前端约定处理
                .csrf().disable()
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
        return http.build();
    }
}
