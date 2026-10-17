package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.PortalInfoView;
import com.example.rbac.dto.PortalSubsystemView;
import com.example.rbac.security.PortalUser;
import com.example.rbac.service.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 统一门户的后端接口（由 portal-frontend 调用）
 * <p>
 * 认证方式与其余管理接口不同：这里认的是授权服务签发的 <b>OAuth2 令牌</b>，
 * 由 {@code PortalAuthenticationFilter} 调 {@code /oauth/check_token} 校验，
 * 且<b>不加载任何 RBAC 权限</b> —— 门户不做权限点判定，它只是「进各子系统的门」。
 * 两条安全链分开（见 {@code SecurityConfig#portalFilterChain}），
 * 因此门户令牌进不了管理接口，管理令牌也进不了这里。
 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;

    /**
     * 用授权码换门户令牌
     * <p>
     * 门户前端的回调页拿到 {@code ?code=xxx} 后调这里。换令牌必须带 client_secret，
     * 只能由服务端做 —— 因此这个接口是门户链上唯一放行匿名访问的（此时用户还没有令牌）。
     */
    @PostMapping("/token")
    public Result<Map<String, Object>> exchangeToken(@RequestBody Map<String, String> body) {
        return Result.success("登录成功", portalService.exchangeCode(body.get("code")));
    }

    /**
     * 发起门户登录：拼接门户自己的授权地址并 302 过去
     * <p>
     * 授权地址上要带 client_secret（本项目对授权端点的定制要求），所以不能作为接口数据
     * 交给前端 —— 前端只做一次导航，URL 由后端拼、302 由后端发。
     */
    @GetMapping("/sso-login")
    public void ssoLogin(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, portalService.portalAuthorizeUrl());
    }

    /**
     * 一键登录的「某一跳」：拼好该子系统的授权地址并 302 过去
     * <p>
     * 门户首页给每个子系统放一个隐藏 iframe，src 指向这里。iframe 跟着 302 走完授权码流程，
     * 子系统自己的回调页把令牌存进它那份 localStorage。前端因此完全不需要（也拿不到）
     * 带密钥的授权地址。
     * <p>
     * 不需要门户令牌：iframe 的导航带不上 Authorization 头。这也不是开放重定向 ——
     * 目标由 {@code code} 在已登记子系统里查出来，不是外部输入。
     */
    @GetMapping("/sso-login/{code}")
    public void subsystemSsoLogin(@PathVariable String code, HttpServletResponse response) {
        String authorizeUrl = portalService.subsystemAuthorizeUrl(code);
        if (authorizeUrl == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, authorizeUrl);
    }

    /**
     * 子系统清单（含各自的授权地址），门户据此渲染卡片并做一键登录
     * <p>
     * 授权地址里带 client_secret，因此这个接口必须带门户令牌才能调。
     */
    @GetMapping("/subsystems")
    public Result<List<PortalSubsystemView>> subsystems() {
        return Result.success(portalService.listSubsystems());
    }

    /**
     * 当前登录状态：身份 + 已登录过哪些子系统
     * <p>
     * 一键登录时前端反复轮询这里，靠 {@code clients} 里是否出现目标子系统来判断那一步完成了。
     */
    @GetMapping("/info")
    public Result<PortalInfoView> info() {
        return Result.success(portalService.info(currentUser()));
    }

    private PortalUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (PortalUser) authentication.getPrincipal();
    }
}
