package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.service.IdpOutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RBAC → 外部通讯录（企业微信/钉钉）手动同步
 */
@RestController
@RequestMapping("/idp/outbound")
@RequiredArgsConstructor
public class IdpOutboundController {

    private final IdpOutboundService outboundService;

    /**
     * 可用的出站提供方（管理端按钮用）
     */
    @GetMapping("/providers")
    public Result<List<Map<String, String>>> providers() {
        return Result.success(outboundService.enabledProviders());
    }

    /**
     * 全量推送：先部门后用户，已存在的按更新处理
     */
    @PostMapping("/sync-all")
    @PreAuthorize("@ss.hasPerm('system:user:sync')")
    public Result<java.util.Map<String, Object>> syncAll(@RequestParam String provider) {
        return Result.success("已加入同步队列", outboundService.pushAll(provider));
    }

    /**
     * 手动把一个用户同步到指定外部提供方（创建或更新）
     */
    @PostMapping("/user/{userId}")
    @PreAuthorize("@ss.hasPerm('system:user:sync')")
    public Result<Void> syncUser(@PathVariable Long userId,
                                 @RequestParam String provider) {
        outboundService.manualSyncUser(userId, provider);
        return Result.success("已加入同步队列", null);
    }

    /**
     * 手动把一个部门同步到指定外部提供方
     */
    @PostMapping("/dept/{deptId}")
    @PreAuthorize("@ss.hasPerm('system:dept:edit')")
    public Result<Void> syncDept(@PathVariable Long deptId,
                                 @RequestParam String provider) {
        outboundService.manualSyncDept(deptId, provider);
        return Result.success("已加入同步队列", null);
    }
}
