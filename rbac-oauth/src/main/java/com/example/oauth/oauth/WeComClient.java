package com.example.oauth.oauth;

import com.example.oauth.config.WeComProperties;
import com.example.oauth.oauth.ClientSecretCipher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信 API 客户端
 * <p>
 * access_token 缓存在 Redis（企业微信有效期 7200 秒，提前 5 分钟过期），
 * 多实例部署时用一把短锁避免并发重复获取。
 */
@Slf4j
@Component
public class WeComClient {

    private static final String TOKEN_KEY = "oauth2:wecom:access_token";
    private static final String TOKEN_LOCK_KEY = "oauth2:wecom:token:lock";

    private final WeComProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ClientSecretCipher secretCipher;
    private final RestTemplate restTemplate;

    public WeComClient(WeComProperties properties,
                       StringRedisTemplate redisTemplate,
                       ClientSecretCipher secretCipher,
                       @Qualifier("weComRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.secretCipher = secretCipher;
        this.restTemplate = restTemplate;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getCorpId())
                && StringUtils.hasText(properties.getAgentId())
                && StringUtils.hasText(resolveSecret())
                && StringUtils.hasText(properties.getCallbackUrl());
    }

    /**
     * 企业微信扫码授权页（浏览器整页跳转）
     */
    public String buildQrConnectUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(properties.getQrConnectUrl())
                .queryParam("login_type", "CorpApp")
                .queryParam("appid", properties.getCorpId())
                .queryParam("agentid", properties.getAgentId())
                // 作为 URI 模板变量展开，确保回调地址被正确 URL 编码
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("state", state)
                .encode()
                .buildAndExpand(properties.getCallbackUrl())
                .toUriString();
    }

    /**
     * 用回调 code 换企业微信 userid
     */
    public String getUserId(String code) {
        String url = properties.getApiBaseUrl() + "/cgi-bin/auth/getuserinfo"
                + "?access_token=" + encode(getAccessToken())
                + "&code=" + encode(code);
        JsonNode node = restTemplate.getForObject(url, JsonNode.class);
        int errcode = node == null ? -1 : node.path("errcode").asInt(-1);
        if (errcode != 0) {
            String errmsg = node == null ? "empty response" : node.path("errmsg").asText();
            throw new WeComException("getuserinfo failed: errcode=" + errcode + ", errmsg=" + errmsg);
        }
        String userid = node.path("userid").asText(null);
        if (!StringUtils.hasText(userid)) {
            // 非企业成员（外部联系人）返回 openid；MVP 只支持企业成员
            userid = node.path("openid").asText(null);
        }
        if (!StringUtils.hasText(userid)) {
            throw new WeComException("getuserinfo returned no userid/openid");
        }
        return userid;
    }

    private String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(TOKEN_LOCK_KEY, "1", 10, TimeUnit.SECONDS));
        try {
            if (!locked) {
                // 其他实例正在刷新，短暂等待后复用它的结果
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    cached = redisTemplate.opsForValue().get(TOKEN_KEY);
                    if (StringUtils.hasText(cached)) {
                        return cached;
                    }
                }
                throw new WeComException("timed out waiting for access_token refresh");
            }

            String url = properties.getApiBaseUrl() + "/cgi-bin/gettoken"
                    + "?corpid=" + encode(properties.getCorpId())
                    + "&corpsecret=" + encode(resolveSecret());
            JsonNode node = restTemplate.getForObject(url, JsonNode.class);
            int errcode = node == null ? -1 : node.path("errcode").asInt(-1);
            if (errcode != 0) {
                String errmsg = node == null ? "empty response" : node.path("errmsg").asText();
                throw new WeComException("gettoken failed: errcode=" + errcode + ", errmsg=" + errmsg);
            }
            String token = node.path("access_token").asText();
            int expiresIn = node.path("expires_in").asInt(7200);
            redisTemplate.opsForValue().set(TOKEN_KEY, token,
                    Math.max(60, expiresIn - 300), TimeUnit.SECONDS);
            return token;
        } finally {
            if (locked) {
                redisTemplate.delete(TOKEN_LOCK_KEY);
            }
        }
    }

    private String resolveSecret() {
        String secret = properties.getSecret();
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        if (secret.startsWith("ENC(") && secret.endsWith(")")) {
            return secretCipher.decrypt(secret.substring(4, secret.length() - 1));
        }
        return secret;
    }

    private String encode(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 企业微信接口异常（不要把 secret/code 写进日志） */
    public static class WeComException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public WeComException(String message) {
            super(message);
        }
    }
}
