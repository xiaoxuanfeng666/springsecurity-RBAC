package com.example.oauth.oauth;

import com.example.oauth.common.Result;
import com.example.oauth.common.ResultCode;
import com.example.oauth.config.JwtPublicKeyConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 授权码回调的联调端点（<b>仅供测试</b>）
 * <p>
 * 授权码模式需要一个能接收 {@code ?code=...} 的第三方回调地址才能真正跑通。本项目自身
 * 提供了一个，把「接收 code → 换令牌 → 解析用户信息」串成一步，便于在浏览器里直接验证：
 * <pre>
 * GET /oauth/authorize?response_type=code&amp;client_id=rbac-third-party&amp;client_secret=client123
 *     &amp;redirect_uri=http://localhost:7071/oauth/test-callback&amp;scope=read&amp;state=abc
 * </pre>
 * 登录授权后浏览器会回到本端点，直接返回换到的令牌与令牌中的用户信息。
 * <p>
 * 注意 {@code client_secret} 是**本项目要求在授权地址上携带并校验**的（标准 OAuth2 不需要，
 * 见 {@code AuthorizeClientSecretFilter}），因此授权地址必须带上它。
 * <p>
 * 使用前提：回调地址必须已登记在客户端的 {@code clientdetails.redirectUrl} 中，否则授权端点
 * 会以「回调地址不匹配」拒绝。示例客户端已内置该地址。
 * <p>
 * <b>安全提示</b>：本端点无需登录即可访问，且会把令牌直接返回给调用方，属于测试工具。
 * 生产环境请把 {@code oauth2.test-callback.enabled} 置为 {@code false} 关闭它。
 */
@Slf4j
@RestController
@ConditionalOnProperty(prefix = "oauth2.test-callback", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OAuth2TestCallbackController {

    /** 换令牌时用的客户端，需与发起授权时使用的客户端一致 */
    @Value("${oauth2.test-callback.client-id}")
    private String clientId;

    @Value("${oauth2.test-callback.client-secret}")
    private String clientSecret;

    private final JwtPublicKeyConfig jwtPublicKeyConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    public OAuth2TestCallbackController(JwtPublicKeyConfig jwtPublicKeyConfig) {
        this.jwtPublicKeyConfig = jwtPublicKeyConfig;
    }

    @GetMapping("/oauth/test-callback")
    public Result<Map<String, Object>> callback(@RequestParam(required = false) String code,
                                                @RequestParam(required = false) String state,
                                                @RequestParam(required = false) String error,
                                                @RequestParam(name = "error_description", required = false) String errorDescription,
                                                HttpServletRequest request) {
        // 用户拒绝授权或参数不合法时，授权服务器会带着 error 回到这里
        if (StringUtils.hasText(error)) {
            String detail = StringUtils.hasText(errorDescription) ? " - " + errorDescription : "";
            return Result.error("授权失败: " + error + detail);
        }
        if (!StringUtils.hasText(code)) {
            return Result.error("回调未收到 code 参数");
        }

        // 换令牌时必须携带与授权请求完全一致的 redirect_uri；
        // getRequestURL() 返回去掉查询串的完整地址，正是当初传给 /oauth/authorize 的那个
        String redirectUri = request.getRequestURL().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("receivedCode", code);
        data.put("state", state);
        data.put("redirectUri", redirectUri);

        Map<String, Object> token = post(tokenEndpoint(), form -> {
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
        });
        data.put("token", token);

        Object accessToken = token.get("access_token");
        //验签access_token
        validateJwtToken((String) accessToken,jwtPublicKeyConfig.getPublicKey());
        if (accessToken != null) {
            // 顺带把令牌里的用户信息解出来，省得再手动调一次 check_token
            data.put("tokenInfo", post(checkTokenEndpoint(),
                    form -> form.add("token", String.valueOf(accessToken))));
        }
        log.info("授权码回调联调: clientId={}, code={}", clientId, code);
        return Result.success("授权码回调成功（此端点为联调工具，生产环境请关闭）", data);
    }


    public static void validateJwtToken(String token, PublicKey publicKey) {
        try{

            Jwt<JwsHeader, Claims> parseClaimsJwt = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(token);

            Claims claims = parseClaimsJwt.getBody();

           log.info("claims:{}",claims);


        }catch(Exception e){

            log.error("校验token异常:{},异常信息:{}",token,e.getMessage());
            e.printStackTrace();
        }
    }


    private Map<String, Object> post(String url, java.util.function.Consumer<MultiValueMap<String, String>> formBuilder) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        formBuilder.accept(form);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.AUTHORIZATION, basicAuth(clientId, clientSecret));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
            return response.getBody() == null ? Collections.emptyMap() : response.getBody();
        } catch (Exception e) {
            // 把授权服务器的原始错误带回去，便于直接看到是哪个参数不对
            log.warn("调用 {} 失败: {}", url, e.getMessage());
            return Collections.singletonMap("requestFailed", e.getMessage());
        }
    }

    private String tokenEndpoint() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/oauth/token").toUriString();
    }

    private String checkTokenEndpoint() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/oauth/check_token").toUriString();
    }

    private static String basicAuth(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
