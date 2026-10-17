package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.SsoSessionView;
import com.example.rbac.security.SsoSessionStore;
import com.example.rbac.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SSO 会话管理（管理端）
 * <p>
 * 在线用户列表来自 Redis 里的 SSO 会话（由授权服务在令牌签发时写入），
 * 「强制下线」删掉该会话 —— 令牌立刻失效、浏览器会话一并销毁，
 * 而不是等令牌自己过期。这是统一登出在管理侧的入口。
 */
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoSessionController {

    private final SsoSessionStore ssoSessionStore;
    private final AuthService authService;

    /**
     * 在线用户列表
     * <p>
     * 按登录时间倒序，最近登录的在最前面 —— 查「谁刚上来了」比查「谁最早登录」常用。
     * 会话里没有登录时间（脏数据）的排在最后。
     */
    @GetMapping("/session")
    @PreAuthorize("@ss.hasPerm('system:sso:list')")
    public Result<List<SsoSessionView>> list() {
        List<SsoSessionView> sessions = ssoSessionStore.listAll().stream()
                .sorted(Comparator.comparing(SsoSessionView::getLoginTime,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        return Result.success(sessions);
    }

    /**
     * 强制某用户下线（需 system:sso:kick）
     * <p>
     * 直接复用管理端既有的「强制下线」实现：它已经做完了该做的校验（不允许踢自己、目标用户必须存在），
     * 并会写一条审计日志。而它内部调用的 {@code TokenStore#removeSession} 现在会连带清除 SSO 会话，
     * 所以这一个调用就同时达成了「管理端下线 + 所有子系统令牌失效」。
     */
    @DeleteMapping("/session/{userId}")
    @PreAuthorize("@ss.hasPerm('system:sso:kick')")
    public Result<Void> kick(@PathVariable Long userId) {
        authService.kickOut(userId);
        return Result.success("已强制下线，该用户在各子系统的登录已同时失效", null);
    }
}
