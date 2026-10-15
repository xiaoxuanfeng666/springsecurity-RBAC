package com.example.rbac.oauth;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 授权端点的未登录入口点：跳转到前端登录页，并带上 {@code oauth2=1} 标记
 * <p>
 * 用自定义入口点而非 Spring Security 默认的 {@code LoginUrlAuthenticationEntryPoint}，有两个原因：
 * <ol>
 *   <li>默认实现只跳到 {@code /login}，前端无从得知这是「OAuth2 授权流程」还是「本系统自身登录」。
 *       两者登录方式不同：本系统用 axios 调 {@code /auth/login} 拿 JWT；OAuth2 授权必须建立浏览器会话，
 *       用原生表单 POST 到 {@code /oauth/login}。故带上标记供前端区分。</li>
 *   <li>登录页在前端，必须跳到<b>浏览器可达的前端地址</b>。若只给相对路径 {@code /login}，
 *       浏览器会按当前源解析 —— 用户从后端源（如 {@code localhost:7070}）发起授权时，
 *       就会跳到后端自己的 {@code /login}，而后端并没有这个页面，最终以 401 结束。
 *       因此这里用配置项 {@code oauth2.login-page-url} 指定绝对地址。</li>
 * </ol>
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
        response.setHeader(HttpHeaders.LOCATION, withOAuthFlag(loginPageUrl));
    }

    /** 追加 oauth2=1 标记，兼容登录页地址本身已带查询串的情况 */
    public static String withOAuthFlag(String loginPageUrl) {
        String separator = loginPageUrl.contains("?") ? "&" : "?";
        return loginPageUrl + separator + "oauth2=1";
    }
}
