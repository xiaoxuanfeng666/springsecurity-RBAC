package com.example.audit.security;

import com.example.audit.common.BusinessException;
import com.example.audit.common.ResultCode;
import com.example.audit.config.OAuth2ClientProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * 与 rbac-oauth 授权服务交互（本系统没有 OAuth2 客户端依赖，全部靠 HTTP 调）
 * <p>
 * 这里只负责登录/刷新流程，都是低频调用：
 * <ul>
 *   <li>{@code POST /oauth/token} grant_type=password —— 密码模式换令牌</li>
 *   <li>{@code POST /oauth/token} grant_type=authorization_code —— 授权码换令牌</li>
 *   <li>{@code POST /oauth/token} grant_type=refresh_token —— access token 过期后由后端静默续期</li>
 *   <li>{@code POST /oauth/sso-logout} —— 登出时让授权服务销毁 SSO 会话</li>
 * </ul>
 * <b>请求鉴权</b>不回调授权服务，改为本地验签（见 {@link JwtTokenVerifier}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2TokenClient {

    private final RestTemplate restTemplate;
    private final OAuth2ClientProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 密码模式：由授权服务拿 RBAC 的 sys_user 校验用户名密码
     */
    public Map<String, Object> passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);
        form.add("scope", properties.getScope());
        return postToken(form, ResultCode.USERNAME_OR_PASSWORD_ERROR);
    }

    /**
     * 授权码模式：用回调带回来的 code 换令牌
     * <p>
     * redirect_uri 必须与授权时用的、以及 clientdetails 里登记的三者完全一致，否则授权服务会拒绝。
     */
    public Map<String, Object> authorizationCodeGrant(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());
        return postToken(form, ResultCode.CODE_INVALID);
    }

    /**
     * 刷新令牌：access token 过期后由本系统后端静默触发（浏览器不接触 refresh token）
     */
    public Map<String, Object> refreshTokenGrant(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form, ResultCode.REFRESH_TOKEN_INVALID);
    }

    /**
     * 统一登出：用会话里的 access token 让授权服务销毁 SSO 会话。
     * <p>
     * 登出必须是幂等的：授权服务不可达、令牌已过期都只记日志，本地会话照常清理。
     */
    public void ssoLogout(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        try {
            restTemplate.postForEntity(properties.getServerUrl() + "/oauth/sso-logout",
                    new HttpEntity<>(headers), Map.class);
        } catch (RestClientException e) {
            log.warn("调用授权服务登出失败，本地会话仍会清理: {}", e.getMessage());
        }
    }

    /**
     * 统一的令牌端点调用
     *
     * @param invalidGrantCode 授权服务回 invalid_grant 时对应的本系统错误码
     *                         （密码模式是"账号密码错误"，授权码模式是"授权码无效"）
     */
    private Map<String, Object> postToken(MultiValueMap<String, String> form, ResultCode invalidGrantCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, basicAuth(properties.getClientId(), properties.getClientSecret()));

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getServerUrl() + "/oauth/token",
                    new HttpEntity<>(form, headers), Map.class);
            if (response.getBody() == null) {
                throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
            }
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw translate(e, invalidGrantCode);
        } catch (RestClientException e) {
            log.error("调用授权服务令牌端点失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
        }
    }

    /**
     * 把授权服务的 OAuth2 标准错误翻译成本系统的错误码
     * <p>
     * 密码模式与授权码模式都会回 invalid_grant，含义不同，靠调用方传入的码区分；
     * 其中"账号已停用/离职"由 Spring Security 的 DisabledException 抛出，
     * 授权服务只能给出 error_description=User is disabled，据此单独识别。
     */
    private BusinessException translate(HttpClientErrorException e, ResultCode invalidGrantCode) {
        String body = e.getResponseBodyAsString();
        String error = readField(body, "error");
        String description = readField(body, "error_description");
        log.warn("授权服务返回错误: error={}, description={}, status={}", error, description, e.getRawStatusCode());

        if ("invalid_grant".equals(error)) {
            if (description != null && description.toLowerCase().contains("disabled")) {
                return new BusinessException(ResultCode.ACCOUNT_DISABLED);
            }
            return new BusinessException(invalidGrantCode);
        }
        // invalid_client 说明本系统的 client_id/client_secret 与库中不一致，属于配置问题
        return new BusinessException(ResultCode.OAUTH_UNAVAILABLE);
    }

    private String readField(String json, String field) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
