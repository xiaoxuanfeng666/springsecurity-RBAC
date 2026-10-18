package com.example.oauth.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.oauth.common.Result;
import com.example.oauth.config.IdpProperties;
import com.example.oauth.dto.IdpBindRequest;
import com.example.oauth.entity.SysUser;
import com.example.oauth.entity.SysUserIdentity;
import com.example.oauth.mapper.SysUserIdentityMapper;
import com.example.oauth.mapper.SysUserMapper;
import com.example.oauth.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多 IdP 登录主流程：认证 → 已绑定/JIT/自助绑定 → 建立 SSO 会话 → 回到授权请求
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdpLoginService {

    private final IdentityProviderRegistry providerRegistry;
    private final IdpStateStore stateStore;
    private final BindTicketStore bindTicketStore;
    private final JitUserProvisioner jitUserProvisioner;
    private final IdpSessionService sessionService;
    private final SysUserIdentityMapper identityMapper;
    private final SysUserMapper userMapper;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final IdpProperties idpProperties;

    @Value("${oauth2.login-page-url}")
    private String loginPageUrl;

    /**
     * 发起外部身份登录
     */
    public void authorize(String providerName, String returnTo, HttpServletResponse response) {
        IdentityProvider provider = providerRegistry.get(providerName);
        if (provider == null || !provider.isEnabled()) {
            redirect(response, errorUrl("idp_disabled"));
            return;
        }
        String safeReturnTo = normalizeReturnTo(returnTo);
        if (safeReturnTo == null) {
            redirect(response, errorUrl("idp_no_return"));
            return;
        }
        String state = stateStore.create(provider.provider(), safeReturnTo);
        redirect(response, provider.buildAuthorizeUrl(state));
    }

    /**
     * 身份提供方回调
     */
    public void callback(String providerName, String code, String state,
                         HttpServletRequest request, HttpServletResponse response) {
        IdpState idpState = stateStore.consume(state);
        if (idpState == null || !providerName.equalsIgnoreCase(idpState.getProvider())) {
            redirect(response, errorUrl("idp_state"));
            return;
        }
        IdentityProvider provider = providerRegistry.get(providerName);
        if (provider == null || !provider.isEnabled()) {
            redirect(response, errorUrl("idp_disabled"));
            return;
        }
        if (!StringUtils.hasText(code)) {
            redirect(response, errorUrl("idp_failed"));
            return;
        }

        try {
            ExternalIdentity external = provider.authenticate(code);
            SysUserIdentity identity = findIdentity(external);

            // 1) 已绑定；2) JIT 自动建号；3) 自助绑定；4) 拒绝
            if (identity == null && idpProperties.getJit().isEnabled()) {
                identity = jitUserProvisioner.provision(external);
            }
            if (identity == null && idpProperties.isSelfBindEnabled()) {
                String ticket = bindTicketStore.create(external, idpState.getReturnTo());
                redirect(response, bindPageUrl(ticket));
                return;
            }
            if (identity == null) {
                redirect(response, errorUrl("idp_unbound"));
                return;
            }

            SysUser user = userMapper.selectById(identity.getUserId());
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
                redirect(response, errorUrl("idp_user_disabled"));
                return;
            }

            LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(user.getUsername());
            sessionService.establish(loginUser, request, response,
                    loginType(provider.provider()));
            identity.setLastLoginTime(LocalDateTime.now());
            identityMapper.updateById(identity);

            redirect(response, idpState.getReturnTo());
        } catch (Exception e) {
            log.warn("外部身份登录失败: provider={}, error={}", providerName, e.getMessage());
            redirect(response, errorUrl("idp_failed"));
        }
    }

    /**
     * 自助绑定：用本地账号密码确认身份后建立绑定并登录
     */
    public Result<Map<String, String>> bind(IdpBindRequest request, HttpServletRequest servletRequest,
                                            HttpServletResponse response) {
        BindTicket ticket = bindTicketStore.get(request.getTicket());
        if (ticket == null) {
            return Result.error("绑定请求已过期，请重新扫码");
        }
        if (ticket.getAttempts() >= 5) {
            bindTicketStore.delete(request.getTicket());
            return Result.error("尝试次数过多，请重新扫码");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            bindTicketStore.incrementAttempts(request.getTicket(), ticket);
            return Result.error("用户名或密码错误");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        SysUserIdentity existing = identityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, ticket.getProvider())
                .eq(SysUserIdentity::getCorpId, ticket.getCorpId())
                .eq(SysUserIdentity::getExternalUserId, ticket.getExternalUserId())
                .last("limit 1"));
        if (existing != null && !existing.getUserId().equals(loginUser.getUser().getId())) {
            return Result.error("该外部账号已绑定其他用户，请联系管理员");
        }
        if (existing == null) {
            SysUserIdentity identity = new SysUserIdentity();
            identity.setUserId(loginUser.getUser().getId());
            identity.setProvider(ticket.getProvider());
            identity.setCorpId(ticket.getCorpId());
            identity.setExternalUserId(ticket.getExternalUserId());
            identity.setUnionId(ticket.getUnionId());
            identity.setStatus(1);
            identity.setBindTime(LocalDateTime.now());
            identity.setLastLoginTime(LocalDateTime.now());
            identity.setRemark("用户自助绑定");
            identity.setCreateTime(LocalDateTime.now());
            identity.setUpdateTime(LocalDateTime.now());
            identityMapper.insert(identity);
        }

        bindTicketStore.delete(request.getTicket());
        sessionService.establish(loginUser, servletRequest, response,
                loginType(ticket.getProvider()));

        Map<String, String> data = new LinkedHashMap<>();
        data.put("returnTo", ticket.getReturnTo());
        data.put("username", loginUser.getUsername());
        log.info("自助绑定成功: username={}, provider={}, externalUserId={}",
                loginUser.getUsername(), ticket.getProvider(), ticket.getExternalUserId());
        return Result.success(data);
    }

    private SysUserIdentity findIdentity(ExternalIdentity external) {
        return identityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, external.getProvider())
                .eq(SysUserIdentity::getCorpId, external.getCorpId())
                .eq(SysUserIdentity::getExternalUserId, external.getExternalUserId())
                .eq(SysUserIdentity::getStatus, 1)
                .last("limit 1"));
    }

    private String loginType(String provider) {
        return provider == null ? "IDP" : provider.toUpperCase();
    }

    private String normalizeReturnTo(String returnTo) {
        if (!StringUtils.hasText(returnTo)) {
            return null;
        }
        if (returnTo.indexOf('\r') >= 0 || returnTo.indexOf('\n') >= 0) {
            return null;
        }
        return returnTo.startsWith("/oauth/") ? returnTo : null;
    }

    private String bindPageUrl(String ticket) {
        String base = idpProperties.getBindPageUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "ticket=" + ticket;
    }

    private String errorUrl(String code) {
        String separator = loginPageUrl.contains("?") ? "&" : "?";
        return loginPageUrl + separator + "error=" + code;
    }

    private void redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, location);
    }
}
