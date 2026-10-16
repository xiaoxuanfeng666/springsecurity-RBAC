package com.example.rbac.controller;

import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.SubsystemMenuForm;
import com.example.rbac.entity.SysSubsystemMenu;
import com.example.rbac.service.SysSubsystemMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/subsystem-menu")
@RequiredArgsConstructor
public class SysSubsystemMenuController {

    private final SysSubsystemMenuService subsystemMenuService;

    /**
     * 指定子系统的菜单树（子系统菜单管理页、角色授权树共用）
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasAnyPerm('system:subsystem:menu:list', 'system:role:menu')")
    public Result<List<SysSubsystemMenu>> tree(@RequestParam Long subsystemId) {
        return Result.success(subsystemMenuService.tree(subsystemId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:subsystem:menu:query')")
    public Result<SysSubsystemMenu> detail(@PathVariable Long id) {
        return Result.success(subsystemMenuService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:subsystem:menu:add')")
    @OperLog(module = "子系统菜单管理", action = AuditAction.CREATE, description = "新增子系统菜单")
    public Result<Void> create(@Valid @RequestBody SubsystemMenuForm form) {
        subsystemMenuService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:subsystem:menu:edit')")
    @OperLog(module = "子系统菜单管理", action = AuditAction.UPDATE, description = "修改子系统菜单")
    public Result<Void> update(@Valid @RequestBody SubsystemMenuForm form) {
        subsystemMenuService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:subsystem:menu:remove')")
    @OperLog(module = "子系统菜单管理", action = AuditAction.DELETE, description = "删除子系统菜单")
    public Result<Void> delete(@PathVariable Long id) {
        subsystemMenuService.delete(id);
        return Result.success("删除成功", null);
    }
}
