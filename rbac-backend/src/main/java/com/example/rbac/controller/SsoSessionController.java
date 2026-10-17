package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.Result;
import com.example.rbac.entity.SsoSessionRecord;
import com.example.rbac.service.SsoSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sso/session")
@RequiredArgsConstructor
public class SsoSessionController {

    private final SsoSessionService ssoSessionService;

    /**
     * SSO 会话分页查询
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:sso:query')")
    public Result<Page<SsoSessionRecord>> page(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String username,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(ssoSessionService.page(pageNum, pageSize, username, status));
    }

    /**
     * 强制下线某条 SSO 会话
     */
    @PostMapping("/kick/{sessionId}")
    @PreAuthorize("@ss.hasPerm('system:sso:kick')")
    public Result<Void> kick(@PathVariable Long sessionId) {
        ssoSessionService.kick(sessionId);
        return Result.success("已强制下线", null);
    }
}
