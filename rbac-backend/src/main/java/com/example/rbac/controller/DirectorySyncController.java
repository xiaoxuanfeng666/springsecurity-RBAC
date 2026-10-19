package com.example.rbac.controller;

import com.example.rbac.common.BusinessException;
import com.example.rbac.common.Result;
import com.example.rbac.common.ResultCode;
import com.example.rbac.config.DirectorySyncProperties;
import com.example.rbac.directory.DirectoryEvent;
import com.example.rbac.service.DirectoryEventService;
import com.example.rbac.service.DirectorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通讯录 / 部门同步：Pull 对账 + Push 事件
 */
@RestController
@RequestMapping("/idp/directory")
@RequiredArgsConstructor
public class DirectorySyncController {

    private final DirectorySyncService syncService;
    private final DirectoryEventService eventService;
    private final DirectorySyncProperties properties;

    /**
     * 手动触发一次拉取对账
     */
    @PostMapping("/sync")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    public Result<Map<String, Object>> sync() {
        return Result.success(syncService.sync());
    }

    /**
     * 外部系统推送用户/部门变更
     * <p>
     * 使用共享密钥鉴权；真实的企业微信/钉钉回调应在网关/适配层完成签名校验与解密后，
     * 转换成 {@link DirectoryEvent} 再调用本接口。
     */
    @PostMapping("/events/{provider}")
    public Result<Map<String, Object>> push(
            @PathVariable String provider,
            @RequestHeader(value = "X-Directory-Token", required = false) String token,
            @RequestBody DirectoryEvent event) {
        if (!StringUtils.hasText(properties.getPushToken())
                || !properties.getPushToken().equals(token)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.success(eventService.handle(provider, event));
    }
}
