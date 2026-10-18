package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.service.DirectorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通讯录 / 部门同步
 */
@RestController
@RequestMapping("/idp/directory")
@RequiredArgsConstructor
public class DirectorySyncController {

    private final DirectorySyncService syncService;

    /**
     * 手动触发一次同步（便于联调与运维）
     */
    @PostMapping("/sync")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    public Result<Map<String, Object>> sync() {
        return Result.success(syncService.sync());
    }
}
