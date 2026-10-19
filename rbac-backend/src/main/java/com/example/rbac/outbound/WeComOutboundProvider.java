package com.example.rbac.outbound;

import com.example.rbac.config.IdpOutboundProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 企业微信出站推送（通讯录写接口）
 * <p>
 * 需要企业微信应用具备通讯录管理权限。access_token 缓存在 Redis。
 */
@Slf4j
@Component
public class WeComOutboundProvider implements OutboundDirectoryProvider {

    public static final String PROVIDER = "wecom";

    private static final String TOKEN_KEY = "oauth2:outbound:wecom:token";
    private static final String TOKEN_LOCK_KEY = "oauth2:outbound:wecom:token:lock";

    private final IdpOutboundProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WeComOutboundProvider(IdpOutboundProperties properties,
                                 StringRedisTemplate redisTemplate,
                                 @Qualifier("directoryRestTemplate") RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return properties.includes(PROVIDER)
                && StringUtils.hasText(properties.getWecomCorpId())
                && StringUtils.hasText(properties.getWecomSecret());
    }

    @Override
    public String createUser(OutboundUser user) {
        Map<String, Object> body = userBody(user);
        JsonNode node = postRaw("/cgi-bin/user/create", body);
        int errcode = errcode(node);
        if (errcode == 60102) {
            // userid 已存在：转为更新，避免全量推送因存量账号中断
            post("/cgi-bin/user/update", body);
            return user.getExternalUserId();
        }
        assertOk(node, "/cgi-bin/user/create");
        return user.getExternalUserId();
    }

    @Override
    public void updateUser(OutboundUser user) {
        post("/cgi-bin/user/update", userBody(user));
    }

    @Override
    public void deleteUser(String externalUserId) {
        get("/cgi-bin/user/delete?userid=" + encode(externalUserId));
    }

    @Override
    public String createDepartment(OutboundDepartment dept) {
        Map<String, Object> body = new LinkedHashMap<>();
        long parentId = parseLong(dept.getParentExternalDeptId(), 1L);
        body.put("name", dept.getName());
        body.put("parentid", parentId);
        body.put("order", dept.getSort());
        JsonNode node = postRaw("/cgi-bin/department/create", body);
        int errcode = errcode(node);
        if (errcode == 60003) {
            // 同名部门已存在：复用该部门，保证重复全量推送幂等
            JsonNode existing = findDepartment(dept.getName(), parentId);
            if (existing != null) {
                return existing.path("id").asText();
            }
        }
        assertOk(node, "/cgi-bin/department/create");
        JsonNode id = node.get("id");
        if (id == null) {
            throw new IllegalStateException("企业微信创建部门未返回 id");
        }
        return id.asText();
    }

    @Override
    public void updateDepartment(OutboundDepartment dept) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", parseLong(dept.getExternalDeptId(), null));
        body.put("name", dept.getName());
        body.put("parentid", parseLong(dept.getParentExternalDeptId(), 1L));
        body.put("order", dept.getSort());
        post("/cgi-bin/department/update", body);
    }

    @Override
    public void deleteDepartment(String externalDeptId) {
        get("/cgi-bin/department/delete?id=" + parseLong(externalDeptId, 0L));
    }

    private Map<String, Object> userBody(OutboundUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userid", user.getExternalUserId());
        body.put("name", StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername());
        if (StringUtils.hasText(user.getAlias())) {
            body.put("alias", user.getAlias());
        }
        if (StringUtils.hasText(user.getPosition())) {
            body.put("position", user.getPosition());
        }
        if (user.getGender() != null && user.getGender() > 0) {
            body.put("gender", user.getGender());
        }
        if (StringUtils.hasText(user.getMobile())) {
            body.put("mobile", user.getMobile());
        }
        if (StringUtils.hasText(user.getEmail())) {
            body.put("email", user.getEmail());
        }
        if (StringUtils.hasText(user.getBizMail())) {
            body.put("biz_mail", user.getBizMail());
        }
        if (StringUtils.hasText(user.getTelephone())) {
            body.put("telephone", user.getTelephone());
        }
        if (StringUtils.hasText(user.getExternalPosition())) {
            body.put("external_position", user.getExternalPosition());
        }
        if (StringUtils.hasText(user.getExternalNickname())) {
            body.put("nickname", user.getExternalNickname());
        }
        if (StringUtils.hasText(user.getAddress())) {
            body.put("address", user.getAddress());
        }
        if (StringUtils.hasText(user.getDirectLeader())) {
            body.put("direct_leader", splitValues(user.getDirectLeader()));
        }
        if (StringUtils.hasText(user.getExtAttr())) {
            try {
                body.put("extattr", objectMapper.readTree(user.getExtAttr()));
            } catch (Exception e) {
                log.warn("扩展属性不是合法 JSON，跳过企业微信 extattr: userid={}",
                        user.getExternalUserId());
            }
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
        List<Integer> orders = new ArrayList<>();
        List<Integer> leaders = new ArrayList<>();
        for (int i = 0; i < departments.size(); i++) {
            orders.add(i == 0 && user.getOrderInDept() != null ? user.getOrderInDept() : 0);
            leaders.add(i == 0 && Boolean.TRUE.equals(user.getLeaderInDept()) ? 1 : 0);
        }
        body.put("department", departments);
        body.put("order", orders);
        body.put("is_leader_in_dept", leaders);
        body.put("enable", user.isEnabled() ? 1 : 0);
        return body;
    }

    private List<String> splitValues(String value) {
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return assertOk(postRaw(path, body), path);
    }

    private JsonNode postRaw(String path, Map<String, Object> body) {
        String url = properties.getWecomApiBaseUrl() + path
                + "?access_token=" + encode(getToken());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        return response.getBody();
    }

    /** 按名称和父部门查找已存在的部门 */
    private JsonNode findDepartment(String name, long parentId) {
        String url = properties.getWecomApiBaseUrl() + "/cgi-bin/department/list?access_token="
                + encode(getToken());
        JsonNode node = restTemplate.getForObject(url, JsonNode.class);
        assertOk(node, "department/list");
        for (JsonNode item : node.path("department")) {
            if (name.equals(item.path("name").asText())
                    && item.path("parentid").asLong() == parentId) {
                return item;
            }
        }
        return null;
    }

    private void get(String path) {
        String url = properties.getWecomApiBaseUrl() + path
                + (path.contains("?") ? "&" : "?") + "access_token=" + encode(getToken());
        JsonNode node = restTemplate.getForObject(url, JsonNode.class);
        assertOk(node, path);
    }

    private JsonNode assertOk(JsonNode node, String api) {
        int errcode = errcode(node);
        if (errcode != 0) {
            String errmsg = node == null ? "empty response" : node.path("errmsg").asText();
            throw new IllegalStateException("企业微信 " + api + " 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }
        return node;
    }

    private int errcode(JsonNode node) {
        return node == null ? -1 : node.path("errcode").asInt(-1);
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
                throw new IllegalStateException("等待企业微信 access_token 超时");
            }
            String url = properties.getWecomApiBaseUrl() + "/cgi-bin/gettoken?corpid="
                    + encode(properties.getWecomCorpId()) + "&corpsecret="
                    + encode(properties.getWecomSecret());
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
