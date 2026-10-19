package com.example.rbac.directory;

import com.example.rbac.config.DirectorySyncProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信通讯录
 * <p>
 * 部门：/cgi-bin/department/list
 * 成员：/cgi-bin/user/list?department_id=1&fetch_child=1（企业微信根部门 ID 为 1）
 */
@Slf4j
@Component
public class WeComDirectoryProvider implements DirectoryProvider {

    public static final String PROVIDER = "wecom";

    private static final String TOKEN_KEY = "oauth2:directory:wecom:token";
    private static final String TOKEN_LOCK_KEY = "oauth2:directory:wecom:token:lock";

    private final DirectorySyncProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    public WeComDirectoryProvider(DirectorySyncProperties properties,
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
                && StringUtils.hasText(properties.getWecomCorpId())
                && StringUtils.hasText(properties.getWecomSecret());
    }

    @Override
    public List<ExternalDepartment> fetchDepartments() {
        String url = properties.getWecomApiBaseUrl() + "/cgi-bin/department/list?access_token="
                + encode(getToken());
        JsonNode node = restTemplate.getForObject(url, JsonNode.class);
        assertOk(node, "department/list");
        List<ExternalDepartment> result = new ArrayList<>();
        for (JsonNode item : node.path("department")) {
            result.add(new ExternalDepartment(
                    item.path("id").asText(),
                    item.path("name").asText(),
                    item.hasNonNull("parentid") ? item.get("parentid").asText() : null,
                    item.path("order").asInt(0)));
        }
        return result;
    }

    @Override
    public List<ExternalMember> fetchMembers() {
        String url = properties.getWecomApiBaseUrl() + "/cgi-bin/user/list?access_token="
                + encode(getToken()) + "&department_id=1&fetch_child=1";
        JsonNode node = restTemplate.getForObject(url, JsonNode.class);
        assertOk(node, "user/list");
        List<ExternalMember> result = new ArrayList<>();
        for (JsonNode item : node.path("userlist")) {
            ExternalMember member = new ExternalMember();
            member.setExternalUserId(item.path("userid").asText());
            member.setName(item.path("name").asText());
            member.setAlias(item.path("alias").asText(null));
            member.setPosition(item.path("position").asText(null));
            member.setGender(item.path("gender").asInt(0));
            member.setBizMail(item.path("biz_mail").asText(null));
            member.setMobile(item.path("mobile").asText(null));
            member.setEmail(item.path("email").asText(null));
            member.setTelephone(item.path("telephone").asText(null));
            member.setDirectLeader(joinTextArray(item.path("direct_leader")));
            member.setExternalPosition(item.path("external_position").asText(null));
            member.setExternalNickname(item.path("external_nickname").asText(null));
            member.setAddress(item.path("address").asText(null));
            if (item.hasNonNull("extattr")) {
                member.setExtAttr(item.get("extattr").toString());
            }
            member.setMainDepartmentId(item.hasNonNull("main_department")
                    ? item.get("main_department").asText() : null);
            List<String> deptIds = new ArrayList<>();
            for (JsonNode dept : item.path("department")) {
                deptIds.add(dept.asText());
            }
            member.setDepartmentIds(deptIds);
            member.setLeaderInDept(hasFlag(item.path("is_leader_in_dept")));
            member.setOrderInDept(item.path("order").isArray() && item.get("order").size() > 0
                    ? item.get("order").get(0).asInt(0) : 0);
            member.setActive(item.path("status").asInt(1) == 1);
            result.add(member);
        }
        return result;
    }

    private String joinTextArray(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.asText());
        }
        return String.join(",", values);
    }

    private boolean hasFlag(JsonNode array) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode node : array) {
            if (node.asInt(0) == 1) {
                return true;
            }
        }
        return false;
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

    private void assertOk(JsonNode node, String api) {
        int errcode = node == null ? -1 : node.path("errcode").asInt(-1);
        if (errcode != 0) {
            String errmsg = node == null ? "empty response" : node.path("errmsg").asText();
            throw new IllegalStateException("企业微信 " + api + " 失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }
    }

    private String encode(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
