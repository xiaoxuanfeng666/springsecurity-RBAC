package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.Result;
import com.example.rbac.entity.SysLoginAudit;
import com.example.rbac.service.SysLoginAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class SysLoginAuditController {

    private final SysLoginAuditService auditService;

    /**
     * 登录/下线审计日志分页查询
     * <p>
     * startTime / endTime 支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:audit:list')")
    public Result<Page<SysLoginAudit>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String startTime,
                                            @RequestParam(required = false) String endTime) {
        return Result.success(auditService.page(pageNum, pageSize, username, action, startTime, endTime));
    }
}
