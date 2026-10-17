package com.example.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.audit.common.Result;
import com.example.audit.entity.AuditLogRecord;
import com.example.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询
 * <p>
 * 只读：日志由 MQ 写入，接口不提供任何修改/删除能力。
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService logService;

    /**
     * 审计日志分页查询
     * <p>
     * startTime / endTime 支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('audit:log:list')")
    public Result<Page<AuditLogRecord>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) String source,
                                             @RequestParam(required = false) String module,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String operatorName,
                                             @RequestParam(required = false) Integer success,
                                             @RequestParam(required = false) String startTime,
                                             @RequestParam(required = false) String endTime) {
        return Result.success(logService.page(pageNum, pageSize, source, module, action,
                operatorName, success, startTime, endTime));
    }

    /**
     * 单条详情：包含完整请求参数（列表里也返回，但前端用抽屉展示时取详情更稳妥）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('audit:log:query')")
    public Result<AuditLogRecord> detail(@PathVariable Long id) {
        return Result.success(logService.detail(id));
    }
}
