package com.example.rbac.controller;

import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.IdentityProviderOption;
import com.example.rbac.dto.UserIdentityForm;
import com.example.rbac.entity.SysUserIdentity;
import com.example.rbac.service.SysUserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户外部身份绑定（企业微信扫码登录）
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserIdentityController {

    private final SysUserIdentityService identityService;

    @GetMapping("/{userId}/identities")
    @PreAuthorize("@ss.hasPerm('system:user:query')")
    public Result<List<SysUserIdentity>> list(@PathVariable Long userId) {
        return Result.success(identityService.list(userId));
    }

    /**
     * 支持绑定的外部身份提供方
     */
    @GetMapping("/identities/providers")
    @PreAuthorize("@ss.hasPerm('system:user:query')")
    public Result<List<IdentityProviderOption>> providers() {
        return Result.success(identityService.supportedProviders());
    }

    @PostMapping("/{userId}/identities")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    @OperLog(module = "用户管理", action = AuditAction.GRANT, description = "绑定企业微信")
    public Result<Void> create(@PathVariable Long userId,
                               @Valid @RequestBody UserIdentityForm form) {
        identityService.create(userId, form);
        return Result.success("绑定成功", null);
    }

    @DeleteMapping("/identity/{id}")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    @OperLog(module = "用户管理", action = AuditAction.DELETE, description = "解绑企业微信")
    public Result<Void> delete(@PathVariable Long id) {
        identityService.delete(id);
        return Result.success("解绑成功", null);
    }
}
