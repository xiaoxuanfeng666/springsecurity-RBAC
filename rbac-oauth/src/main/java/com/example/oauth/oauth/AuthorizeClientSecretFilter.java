package com.example.oauth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授权端点（{@code /oauth/authorize}）的客户端密钥校验
 * <p>
 * <b>这偏离 OAuth2 标准做法，是本项目的定制要求。</b>标准里授权端点只认 {@code client_id}，
 * 密钥留到令牌端点（{@code /oauth/token}）才用 —— 因为授权端点是<b>浏览器跳转</b>访问的，
 * 密钥写进 URL 会落在浏览器历史、Referer 头、网关访问日志里。
 * <p>
 * 本类按项目要求改为：{@code /oauth/authorize} 必须带上 {@code client_secret} 且服务端校验，
 * 缺失或不匹配一律以 {@code invalid_client} 拒绝。
 * <p>
 * 使用该策略时请注意配套措施：
 * <ul>
 *   <li>授权地址会进入浏览器历史与访问日志 —— 生产环境应在网关层对含 {@code client_secret}
 *       的 URL 做日志脱敏，或仅在内网/信任环境使用；</li>
 *   <li>登录后跳回授权页的地址同样带密钥，会落到 {@code RedisRequestCache} 的缓存里；</li>
 *   <li>标准 OAuth2 客户端库不会在授权请求里带密钥，接入第三方时需自行拼接参数。</li>
 * </ul>
 */
@RequiredArgsConstructor
public class AuthorizeClientSecretFilter extends OncePerRequestFilter {

    private static final String AUTHORIZE_ENDPOINT = "/oauth/authorize";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_SECRET = "client_secret";

    private final MybatisClientDetailsService clientDetailsService;
    private final ClientSecretCipher clientSecretCipher;
    private final ObjectMapper objectMapper;

    /** 只拦授权端点；其余（确认页、错误页、登录处理）交给后面的过滤器 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !AUTHORIZE_ENDPOINT.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientId = request.getParameter(PARAM_CLIENT_ID);
        if (!StringUtils.hasText(clientId)) {
            // 连 client_id 都没有，交给授权端点按标准报「缺少参数」，此处不越俎代庖
            filterChain.doFilter(request, response);
            return;
        }

        if (!isSecretValid(clientId, request.getParameter(PARAM_CLIENT_SECRET))) {
            writeInvalidClient(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSecretValid(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientSecret)) {
            return false;
        }
        try {
            BaseClientDetails clientDetails = clientDetailsService.loadClientByClientId(clientId);
            return clientSecretCipher.matches(clientSecret, clientDetails.getClientSecret());
        } catch (ClientRegistrationException | IllegalArgumentException e) {
            // 客户端不存在、未配置密钥、密钥解不开等，一律按认证失败处理（不向外暴露具体原因）
            return false;
        }
    }

    private void writeInvalidClient(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_client");
        body.put("error_description", "授权请求缺少客户端密钥或密钥不正确");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
