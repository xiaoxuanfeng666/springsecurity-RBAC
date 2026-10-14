package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.Result;
import com.example.rbac.entity.SysOperationLog;
import com.example.rbac.service.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogService logService;

    /**
     * 操作日志分页查询
     * <p>
     * startTime / endTime 支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:audit:list')")
    public Result<Page<SysOperationLog>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(required = false) String module,
                                              @RequestParam(required = false) String action,
                                              @RequestParam(required = false) Integer success,
                                              @RequestParam(required = false) String startTime,
                                              @RequestParam(required = false) String endTime) {
        return Result.success(logService.page(pageNum, pageSize, username, module,
                action, success, startTime, endTime));
    }
}
