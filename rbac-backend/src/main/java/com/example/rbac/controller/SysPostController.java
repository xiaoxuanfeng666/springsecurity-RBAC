package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.PostForm;
import com.example.rbac.entity.SysPost;
import com.example.rbac.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:post:list')")
    public Result<Page<SysPost>> page(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String postName) {
        return Result.success(postService.page(pageNum, pageSize, postName));
    }

    /**
     * 全部启用岗位：岗位管理页与用户表单多选框共用
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPerm('system:post:list', 'system:user:list', 'system:user:query', "
            + "'system:user:add', 'system:user:edit')")
    public Result<List<SysPost>> list() {
        return Result.success(postService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:post:query')")
    public Result<SysPost> detail(@PathVariable Long id) {
        return Result.success(postService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:post:add')")
    @OperLog(module = "岗位管理", action = AuditAction.CREATE, description = "新增岗位")
    public Result<Void> create(@Valid @RequestBody PostForm form) {
        postService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:post:edit')")
    @OperLog(module = "岗位管理", action = AuditAction.UPDATE, description = "修改岗位")
    public Result<Void> update(@Valid @RequestBody PostForm form) {
        postService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:post:remove')")
    @OperLog(module = "岗位管理", action = AuditAction.DELETE, description = "删除岗位")
    public Result<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return Result.success("删除成功", null);
    }
}
