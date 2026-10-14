package com.example.rbac.controller;

import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.MenuForm;
import com.example.rbac.entity.SysMenu;
import com.example.rbac.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /**
     * 全部菜单树（菜单管理页、角色授权树共用）
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasAnyPerm('system:menu:list', 'system:role:menu')")
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.tree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:menu:query')")
    public Result<SysMenu> detail(@PathVariable Long id) {
        return Result.success(menuService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:menu:add')")
    @OperLog(module = "菜单管理", action = AuditAction.CREATE, description = "新增菜单")
    public Result<Void> create(@Valid @RequestBody MenuForm form) {
        menuService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:menu:edit')")
    @OperLog(module = "菜单管理", action = AuditAction.UPDATE, description = "修改菜单")
    public Result<Void> update(@Valid @RequestBody MenuForm form) {
        menuService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:menu:remove')")
    @OperLog(module = "菜单管理", action = AuditAction.DELETE, description = "删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success("删除成功", null);
    }
}
