package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.config.PortalProperties;
import com.example.rbac.dto.PortalInfoView;
import com.example.rbac.dto.PortalSubsystemView;
import com.example.rbac.dto.SsoSessionView;
import com.example.rbac.entity.ClientDetails;
import com.example.rbac.entity.SysSubsystem;
import com.example.rbac.mapper.ClientDetailsMapper;
import com.example.rbac.mapper.SysSubsystemMapper;
import com.example.rbac.security.ClientSecretCipher;
import com.example.rbac.security.PortalUser;
import com.example.rbac.security.SsoSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一门户的后端支撑
 * <p>
 * 门户自己不保存任何东西：子系统清单来自 {@code sys_subsystem} × {@code clientdetails}，
 * 登录状态来自 Redis 里的 SSO 会话，令牌由授权服务签发。这里的职责只有三件：
 * <ol>
 *   <li>把「有哪些子系统可以进、怎么进」组装成前端能直接用的卡片数据；</li>
 *   <li>用授权码换门户令牌（换令牌要带 client_secret，只能在服务端做）；</li>
 *   <li>把 SSO 会话里「已登录过哪些子系统」回给前端，供一键登录判断进度。</li>
 * </ol>
 *
 * <h3>子系统与 OAuth2 客户端的对应关系</h3>
 * 约定 {@code sys_subsystem.subsystem_code} == {@code clientdetails.appId}（如 order-center、marketing）。
 * 这是本项目的约定，不是数据库约束 —— 因此当某个子系统在 clientdetails 里找不到客户端时，
 * 不报错，而是把它标记为「不可用」返回，页面上给出提示。
 * 若将来要支持「一个子系统多个客户端」，这里需要改成显式配置关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalService {

    private final SysSubsystemMapper subsystemMapper;
    private final ClientDetailsMapper clientDetailsMapper;
    private final ClientSecretCipher clientSecretCipher;
    private final PortalProperties properties;
    private final SsoSessionStore ssoSessionStore;
    private final RestTemplate restTemplate;

    /** 子系统状态：1 启用 */
    private static final int STATUS_ENABLED = 1;

    /**
     * 门户自己的授权地址（由 {@code GET /portal/sso-login} 直接 302 出去，不返回给前端）
     * <p>
     * 本项目定制的授权端点要求地址上带 client_secret。若把地址作为接口数据交给前端，
     * 密钥就会出现在前端代码、浏览器网络面板与前端日志里 —— 所以这里只给后端自己用，
     * 由后端发 302 让浏览器跟着走，前端全程看不到 client_id / client_secret。
     */
    public String portalAuthorizeUrl() {
        return buildAuthorizeUrl(properties.getClientId(), properties.getClientSecret(),
                properties.getRedirectUri(), properties.getScope());
    }

    /**
     * 某个子系统的授权地址（由 {@code GET /portal/sso-login/{code}} 直接 302 出去）
     * <p>
     * 一键登录的隐藏 iframe 指向的就是那个 302 端点，因此前端不再需要（也拿不到）
     * 带密钥的授权地址 —— 门户接口只下发子系统的编码、名称与入口地址。
     *
     * @return 授权地址；子系统不存在或客户端配置不全时返回 null
     */
    public String subsystemAuthorizeUrl(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        ClientDetails client = clientDetailsMapper.selectById(code);
        if (client == null) {
            return null;
        }
        String secret = clientSecretCipher.decrypt(client.getAppSecret());
        String redirectUri = firstOf(client.getRedirectUrl());
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(redirectUri)) {
            return null;
        }
        return buildAuthorizeUrl(client.getAppId(), secret, redirectUri,
                properties.getSubsystemScope());
    }

    /**
     * 可进入的子系统清单
     * <p>
     * <b>刻意不含授权地址</b>：那个地址上带 client_secret，只能由后端持有并 302 出去
     * （见 {@link #subsystemAuthorizeUrl}）。前端拿到的只有编码、名称、入口地址与可用状态。
     */
    public List<PortalSubsystemView> listSubsystems() {
        List<SysSubsystem> subsystems = subsystemMapper.selectList(
                new LambdaQueryWrapper<SysSubsystem>()
                        .eq(SysSubsystem::getStatus, STATUS_ENABLED)
                        .orderByAsc(SysSubsystem::getSort));

        List<PortalSubsystemView> views = new ArrayList<>();
        for (SysSubsystem subsystem : subsystems) {
            PortalSubsystemView view = new PortalSubsystemView();
            view.setCode(subsystem.getSubsystemCode());
            view.setName(subsystem.getSubsystemName());

            ClientDetails client = clientDetailsMapper.selectById(subsystem.getSubsystemCode());
            if (client == null) {
                view.setReady(false);
                view.setUnavailableReason("未在客户端管理里配置名为 " + subsystem.getSubsystemCode()
                        + " 的 OAuth2 客户端");
                views.add(view);
                continue;
            }
            String secret = clientSecretCipher.decrypt(client.getAppSecret());
            String redirectUri = firstOf(client.getRedirectUrl());
            if (!StringUtils.hasText(secret) || !StringUtils.hasText(redirectUri)) {
                view.setReady(false);
                view.setUnavailableReason("客户端 " + client.getAppId() + " 缺少密钥或回调地址");
                views.add(view);
                continue;
            }

            view.setClientId(client.getAppId());
            view.setEntryUrl(originOf(redirectUri));
            view.setReady(true);
            views.add(view);
        }
        return views;
    }

    /**
     * 当前登录状态：身份 + 已登录过的子系统
     */
    public PortalInfoView info(PortalUser user) {
        PortalInfoView view = new PortalInfoView();
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());

        // 会话一定存在：能通过 /oauth/check_token 就说明授权服务那边校验过会话了。
        // 但为稳妥起见仍按可能为空处理，避免一个竞态（刚好在此时登出）导致 500。
        SsoSessionView session = ssoSessionStore.read(user.getUsername());
        if (session != null) {
            view.setNickname(session.getNickname());
            view.setLoginTime(session.getLoginTime());
            view.setClients(session.getClients() == null ? Collections.emptyList() : session.getClients());
        }
        else {
            view.setClients(Collections.emptyList());
        }
        return view;
    }

    /**
     * 授权码换门户令牌
     * <p>
     * 必须由服务端做：换令牌要带 client_secret，放进浏览器等于把门户客户端密钥公开。
     * <p>
     * 返回时把 OAuth2 的蛇形字段名（{@code access_token}）转成项目里统一的驼峰
     * —— 子系统那边同样如此（见 order-backend 的 {@code TokenResponse}），
     * 前端不必为一个接口记两套命名。
     */
    public Map<String, Object> exchangeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "缺少授权码");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        // redirect_uri 必须与授权时用的、以及 clientdetails 里登记的三者完全一致
        form.add("redirect_uri", properties.getRedirectUri());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION,
                basicAuth(properties.getClientId(), properties.getClientSecret()));

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.getServerUrl() + "/oauth/token",
                    new HttpEntity<>(form, headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new BusinessException(ResultCode.ERROR.getCode(), "授权服务未返回令牌");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accessToken", body.get("access_token"));
            result.put("refreshToken", body.get("refresh_token"));
            result.put("expiresIn", body.get("expires_in"));
            log.info("门户登录成功，已用授权码换到令牌");
            return result;
        }
        catch (HttpClientErrorException e) {
            // 授权码一次性、且有效期只有几分钟：重复使用或超时都会走到这里
            log.warn("门户换令牌失败: {} {}", e.getRawStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(),
                    "授权码无效或已过期，请重新登录");
        }
        catch (RestClientException e) {
            log.error("调用授权服务令牌端点失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.ERROR.getCode(), "授权服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 拼装授权地址。
     * <p>
     * 带 client_secret 是本项目的定制要求（标准 OAuth2 的授权地址只带 client_id）——
     * 授权端点的 {@code AuthorizeClientSecretFilter} 会强制校验它。
     * state 固定为 portal：子系统回调页不做 state 校验（它只认 code），
     * 这里留着是为了让授权服务的访问日志能看出这次授权来自门户。
     */
    private String buildAuthorizeUrl(String clientId, String clientSecret, String redirectUri, String scope) {
        return UriComponentsBuilder.fromHttpUrl(properties.getServerUrl() + "/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", "portal")
                .build()
                .toUriString();
    }

    /** redirectUrl 允许逗号分隔多个（第三方客户端常见），取第一个作为回调地址 */
    private String firstOf(String commaSeparated) {
        if (!StringUtils.hasText(commaSeparated)) {
            return null;
        }
        String first = commaSeparated.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * 从回调地址推出子系统前端的入口地址：{@code http://localhost:5176/oauth/callback} → {@code http://localhost:5176}
     * <p>
     * 用回调地址而不是另加一个「入口地址」字段，是为了不动 sys_subsystem 的表结构与配套的管理页面。
     * 解析失败（回调地址配得不规范）时返回原串，页面上的链接至少还能点。
     */
    private String originOf(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return redirectUri;
            }
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() > 0) {
                origin.append(':').append(uri.getPort());
            }
            return origin.toString();
        }
        catch (IllegalArgumentException e) {
            log.warn("回调地址无法解析为 URI，入口地址原样返回: {}", redirectUri);
            return redirectUri;
        }
    }

    private String basicAuth(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
