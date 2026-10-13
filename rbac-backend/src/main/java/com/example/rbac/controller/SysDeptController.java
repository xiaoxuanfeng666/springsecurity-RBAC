package com.example.rbac.controller;

import com.example.rbac.common.Result;
import com.example.rbac.dto.DeptForm;
import com.example.rbac.entity.SysDept;
import com.example.rbac.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /**
     * 部门树：部门管理页与用户表单的部门选择器共用
     */
    @GetMapping("/tree")
    @PreAuthorize("@ss.hasAnyPerm('system:dept:list', 'system:user:list', 'system:user:query')")
    public Result<List<SysDept>> tree() {
        return Result.success(deptService.tree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:dept:query')")
    public Result<SysDept> detail(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:dept:add')")
    public Result<Void> create(@Valid @RequestBody DeptForm form) {
        deptService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:dept:edit')")
    public Result<Void> update(@Valid @RequestBody DeptForm form) {
        deptService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:dept:remove')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.success("删除成功", null);
    }
}
