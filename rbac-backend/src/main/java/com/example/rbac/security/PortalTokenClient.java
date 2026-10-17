package com.example.rbac.security;

import com.example.rbac.config.PortalProperties;
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

import java.util.Collections;
import java.util.Map;

/**
 * 门户与授权服务交互（只有一件事：校验令牌）
 * <p>
 * 门户不换令牌 —— 换令牌发生在浏览器回调页上由门户后端做，这里只负责「这个 Bearer 令牌是谁的」。
 * {@code /oauth/check_token} 是公开端点，但仍按惯例带上门户客户端凭证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortalTokenClient {

    private final RestTemplate restTemplate;
    private final PortalProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 校验令牌，成功返回门户登录主体；令牌无效/过期/授权服务不可达一律返回 null
     * <p>
     * 注意这里拿到的是「授权服务认为有效的令牌」—— 而授权服务在统一登出后会拒绝该用户的令牌，
     * 所以用户一旦登出，门户这边的令牌也会立刻失效，不需要门户自己做任何事。
     */
    public PortalUser checkToken(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getServerUrl() + "/oauth/check_token",
                    new HttpEntity<>(form, headers), Map.class);
            Map<String, Object> info = response.getBody();
            if (info == null) {
                return null;
            }
            Object active = info.get("active");
            if (active != null && !Boolean.TRUE.equals(active)) {
                return null;
            }
            Object username = info.get("user_name");
            if (username == null) {
                // 客户端凭证模式的令牌没有 user_name —— 门户不接受这类令牌（门户代表的是人）
                log.warn("令牌校验结果里没有 user_name，门户不接受非用户类令牌");
                return null;
            }

            Long userId = null;
            String nickname = null;
            Object additional = info.get("additionalInfo");
            if (additional != null) {
                JsonNode node = objectMapper.valueToTree(additional);
                if (node.hasNonNull("userId")) {
                    userId = node.get("userId").asLong();
                }
                if (node.hasNonNull("nickName")) {
                    nickname = node.get("nickName").asText();
                }
            }
            return new PortalUser(username.toString(), nickname, userId);
        }
        catch (HttpClientErrorException e) {
            // 令牌无效/过期/已被统一登出：授权服务返回 400 invalid_token，属于正常鉴权失败
            log.debug("门户令牌校验未通过: {}", e.getResponseBodyAsString());
            return null;
        }
        catch (RestClientException e) {
            log.error("调用授权服务校验令牌失败: {}", e.getMessage());
            return null;
        }
    }
}
