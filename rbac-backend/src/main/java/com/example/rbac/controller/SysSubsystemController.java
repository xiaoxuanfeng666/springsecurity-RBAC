package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.SubsystemForm;
import com.example.rbac.entity.SysSubsystem;
import com.example.rbac.service.SysSubsystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/subsystem")
@RequiredArgsConstructor
public class SysSubsystemController {

    private final SysSubsystemService subsystemService;

    /**
     * 全部启用子系统（下拉框使用）
     */
    @GetMapping("/list")
    public Result<List<SysSubsystem>> list() {
        return Result.success(subsystemService.listAll());
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:subsystem:list')")
    public Result<Page<SysSubsystem>> page(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) String subsystemName) {
        return Result.success(subsystemService.page(pageNum, pageSize, subsystemName));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:subsystem:query')")
    public Result<SysSubsystem> detail(@PathVariable Long id) {
        return Result.success(subsystemService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:subsystem:add')")
    @OperLog(module = "子系统管理", action = AuditAction.CREATE, description = "新增子系统")
    public Result<Void> create(@Valid @RequestBody SubsystemForm form) {
        subsystemService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:subsystem:edit')")
    @OperLog(module = "子系统管理", action = AuditAction.UPDATE, description = "修改子系统")
    public Result<Void> update(@Valid @RequestBody SubsystemForm form) {
        subsystemService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:subsystem:remove')")
    @OperLog(module = "子系统管理", action = AuditAction.DELETE, description = "删除子系统")
    public Result<Void> delete(@PathVariable Long id) {
        subsystemService.delete(id);
        return Result.success("删除成功", null);
    }
}
