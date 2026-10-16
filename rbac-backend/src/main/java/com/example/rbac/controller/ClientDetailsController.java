package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.ClientForm;
import com.example.rbac.entity.ClientDetails;
import com.example.rbac.service.ClientDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
public class ClientDetailsController {

    private final ClientDetailsService clientDetailsService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:client:list')")
    public Result<Page<ClientDetails>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String appId) {
        return Result.success(clientDetailsService.page(pageNum, pageSize, appId));
    }

    @GetMapping("/{appId}")
    @PreAuthorize("@ss.hasPerm('system:client:query')")
    public Result<ClientDetails> detail(@PathVariable String appId) {
        return Result.success(clientDetailsService.getById(appId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:client:add')")
    @OperLog(module = "客户端管理", action = AuditAction.CREATE, description = "新增客户端")
    public Result<Void> create(@Valid @RequestBody ClientForm form) {
        clientDetailsService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:client:edit')")
    @OperLog(module = "客户端管理", action = AuditAction.UPDATE, description = "修改客户端")
    public Result<Void> update(@Valid @RequestBody ClientForm form) {
        clientDetailsService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{appId}")
    @PreAuthorize("@ss.hasPerm('system:client:remove')")
    @OperLog(module = "客户端管理", action = AuditAction.DELETE, description = "删除客户端")
    public Result<Void> delete(@PathVariable String appId) {
        clientDetailsService.delete(appId);
        return Result.success("删除成功", null);
    }
}
