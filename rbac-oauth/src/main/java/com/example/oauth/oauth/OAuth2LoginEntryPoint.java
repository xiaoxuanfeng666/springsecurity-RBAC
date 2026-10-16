package com.example.oauth.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 授权端点的未登录入口点：302 跳到 OAuth2 登录页（rbac-oauth-web）
 * <p>
 * 用自定义入口点而非 Spring Security 默认的 {@code LoginUrlAuthenticationEntryPoint}：
 * 那边的跳转目标由容器内的登录页配置推导，这里只想「按 {@code oauth2.login-page-url}
 * 原样发出一个 302」，用一个直白的实现更不容易出错。
 * <p>
 * 登录页地址通常是<b>绝对地址</b>：登录页属于 rbac-oauth-web 这个独立工程，与授权端点不同源时，
 * 给相对路径 {@code /login} 会被浏览器按当前源解析 —— 用户从 {@code localhost:7071} 发起授权时
 * 就会跳到授权服务自己的 {@code /login}，而那里没有页面，最终以 404/401 收场。
 * 若前端与授权服务同源部署（生产环境由 Nginx 统一入口），配成相对路径也可以。
 * <p>
 * 登录成功后的「跳回原地址」由 Spring Security 的请求缓存机制完成
 * （{@code ExceptionTranslationFilter} 在调用本入口点前已保存原始请求），无需在此处理。
 */
public class OAuth2LoginEntryPoint implements AuthenticationEntryPoint {

    private final String loginPageUrl;

    public OAuth2LoginEntryPoint(String loginPageUrl) {
        this.loginPageUrl = loginPageUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, loginPageUrl);
    }

    /**
     * 追加 error=1 标记，供登录页提示「用户名或密码错误」。
     * <p>
     * 兼容登录页地址本身已带查询串的情况：只在没有 {@code ?} 时才用 {@code ?} 起头。
     */
    public static String withErrorFlag(String loginPageUrl) {
        String separator = loginPageUrl.contains("?") ? "&" : "?";
        return loginPageUrl + separator + "error=1";
    }
}
