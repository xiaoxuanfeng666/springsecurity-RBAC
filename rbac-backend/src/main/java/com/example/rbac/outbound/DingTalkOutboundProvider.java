package com.example.rbac.outbound;

import com.example.rbac.config.IdpOutboundProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 钉钉出站推送（通讯录写接口）
 * <p>
 * 使用企业内部应用的 AppKey/AppSecret 获取 access_token，需要开通通讯录管理权限。
 */
@Slf4j
@Component
public class DingTalkOutboundProvider implements OutboundDirectoryProvider {

    public static final String PROVIDER = "dingtalk";

    private static final String TOKEN_KEY = "oauth2:outbound:dingtalk:token";
    private static final String TOKEN_LOCK_KEY = "oauth2:outbound:dingtalk:token:lock";

    private final IdpOutboundProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    public DingTalkOutboundProvider(IdpOutboundProperties properties,
                                    StringRedisTemplate redisTemplate,
                                    @Qualifier("directoryRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return properties.includes(PROVIDER)
                && StringUtils.hasText(properties.getDingtalkAppKey())
                && StringUtils.hasText(properties.getDingtalkAppSecret());
    }

    @Override
    public String createUser(OutboundUser user) {
        JsonNode node = post("/topapi/v2/user/create", userBody(user));
        JsonNode result = node.path("result");
        String userid = result.path("userid").asText(null);
        return StringUtils.hasText(userid) ? userid : user.getExternalUserId();
    }

    @Override
    public void updateUser(OutboundUser user) {
        post("/topapi/v2/user/update", userBody(user));
    }

    @Override
    public void deleteUser(String externalUserId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userid", externalUserId);
        post("/topapi/v2/user/delete", body);
    }

    @Override
    public String createDepartment(OutboundDepartment dept) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", dept.getName());
        body.put("parent_id", parseLong(dept.getParentExternalDeptId(), 1L));
        body.put("order", dept.getSort());
        JsonNode node = post("/topapi/v2/department/create", body);
        JsonNode id = node.path("result").path("dept_id");
        if (id.isMissingNode() || id.isNull()) {
            throw new IllegalStateException("钉钉创建部门未返回 dept_id");
        }
        return id.asText();
    }

    @Override
    public void updateDepartment(OutboundDepartment dept) {
        Long id = parseLong(dept.getExternalDeptId(), null);
        if (id == null) {
            throw new IllegalStateException("钉钉部门ID无效: " + dept.getExternalDeptId());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dept_id", id);
        body.put("name", dept.getName());
        body.put("parent_id", parseLong(dept.getParentExternalDeptId(), 1L));
        body.put("order", dept.getSort());
        post("/topapi/v2/department/update", body);
    }

    @Override
    public void deleteDepartment(String externalDeptId) {
        Long id = parseLong(externalDeptId, null);
        if (id == null) {
            throw new IllegalStateException("钉钉部门ID无效: " + externalDeptId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dept_id", id);
        post("/topapi/v2/department/delete", body);
    }

    private Map<String, Object> userBody(OutboundUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userid", user.getExternalUserId());
        body.put("name", StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername());
        if (StringUtils.hasText(user.getEmployeeNo())) {
            body.put("job_number", user.getEmployeeNo());
        }
        if (StringUtils.hasText(user.getPosition())) {
            body.put("title", user.getPosition());
        }
        if (StringUtils.hasText(user.getMobile())) {
            body.put("mobile", user.getMobile());
        }
        String email = StringUtils.hasText(user.getBizMail()) ? user.getBizMail() : user.getEmail();
        if (StringUtils.hasText(email)) {
            body.put("email", email);
        }
        if (StringUtils.hasText(user.getTelephone())) {
            body.put("telephone", user.getTelephone());
        }
        if (StringUtils.hasText(user.getAddress())) {
            body.put("work_place", user.getAddress());
        }
        if (StringUtils.hasText(user.getExtAttr())) {
            body.put("extension", user.getExtAttr());
        }
        List<Long> departments = new ArrayList<>();
        if (user.getDepartmentIds() != null) {
            for (String id : user.getDepartmentIds()) {
                Long parsed = parseLong(id, null);
                if (parsed != null) {
                    departments.add(parsed);
                }
            }
        }
        if (departments.isEmpty()) {
            departments.add(1L);
        }
        body.put("dept_id_list", departments);
        if (!user.isEnabled()) {
            log.warn("钉钉不支持通过通讯录接口停用成员，仅在本地停用: userid={}", user.getExternalUserId());
        }
        return body;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String url = properties.getDingtalkApiBaseUrl() + path
                + "?access_token=" + encode(getToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        return assertOk(response.getBody(), path);
    }

    private JsonNode assertOk(JsonNode node, String api) {
        int errcode = node == null ? -1 : node.path("errcode").asInt(-1);
        if (errcode != 0) {
            String errmsg = node == null ? "empty response" : node.path("errmsg").asText();
            throw new IllegalStateException("钉钉 " + api + " 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }
        return node;
    }

    private String getToken() {
        String cached = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (StringUtils.hasText(cached)) {
            return cached;
        }
        boolean locked = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(TOKEN_LOCK_KEY, "1", 10, TimeUnit.SECONDS));
        try {
            if (!locked) {
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
                throw new IllegalStateException("等待钉钉 access_token 超时");
            }
            String url = properties.getDingtalkApiBaseUrl() + "/gettoken?appkey="
                    + encode(properties.getDingtalkAppKey()) + "&appsecret="
                    + encode(properties.getDingtalkAppSecret());
            JsonNode node = restTemplate.getForObject(url, JsonNode.class);
            assertOk(node, "gettoken");
            String token = node.path("access_token").asText();
            int expires = node.path("expires_in").asInt(7200);
            redisTemplate.opsForValue().set(TOKEN_KEY, token,
                    Math.max(60, expires - 300), TimeUnit.SECONDS);
            return token;
        } finally {
            if (locked) {
                redisTemplate.delete(TOKEN_LOCK_KEY);
            }
        }
    }

    private Long parseLong(String value, Long defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String encode(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
