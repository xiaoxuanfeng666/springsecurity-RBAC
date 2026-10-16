package com.example.oauth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.oauth.common.Result;
import com.example.oauth.dto.OpenApiUserView;
import com.example.oauth.entity.SysUser;
import com.example.oauth.oauth.OpenApiCaller;
import com.example.oauth.service.OpenApiUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 第三方开放接口（{@code /openapi/**}）
 * <p>
 * 调用方持本服务签发的 OAuth2 访问令牌（{@code Authorization: Bearer <token>}），按 <b>scope</b>
 * 粗粒度鉴权，与 RBAC 权限点无关。鉴权规则集中在本链配置里（{@code OpenApiSecurityConfig}），
 * 因此这里的接口方法上没有 {@code @PreAuthorize}。
 * <p>
 * 这几只是<b>示范接口</b>：用来验证「第三方令牌 → scope 鉴权」这条链路可用。
 * 真正对外提供业务数据时，请按同样的模式新增接口，并在 {@code OpenApiSecurityConfig}
 * 的白名单里显式放行（不然会被兜底的 denyAll 拒绝）。
 */
@RestController
@RequestMapping("/openapi")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenApiUserService openApiUserService;

    /**
     * 当前令牌的身份与 scope —— 任意有效令牌都可调用，便于第三方拿到令牌后自检。
     * <p>
     * 用户类授权（授权码/密码）下 subject 是用户名；客户端凭证模式下没有用户，
     * subject 会退化成客户端 id（等于 clientId）。
     */
    @GetMapping("/userinfo")
    public Result<Map<String, Object>> userinfo() {
        OpenApiCaller caller = currentCaller();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", caller.getSubject());
        data.put("clientId", caller.getClientId());
        data.put("scopes", caller.getScopes());
        return Result.success(data);
    }

    /** 读取用户列表，需要 scope {@code read} */
    @GetMapping("/users")
    public Result<Page<OpenApiUserView>> users(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) Integer status) {
        Page<SysUser> source = openApiUserService.page(pageNum, pageSize, username, status);
        Page<OpenApiUserView> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        List<OpenApiUserView> records = source.getRecords().stream()
                .map(OpenApiUserView::from)
                .collect(Collectors.toList());
        target.setRecords(records);
        return Result.success(target);
    }

    /**
     * 修改用户状态，需要 scope {@code write}
     * <p>
     * status 取值：1 在职 / 0 停用 / 2 离职。注意停用与离职都会让该用户立即下线
     * （本系统是单会话模型），第三方调用前请确认业务影响。
     */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        openApiUserService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    private OpenApiCaller currentCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OpenApiCaller) {
            return (OpenApiCaller) authentication.getPrincipal();
        }
        // 本链已要求认证，正常不会走到这里
        throw new IllegalStateException("当前请求不是第三方令牌调用");
    }
}
