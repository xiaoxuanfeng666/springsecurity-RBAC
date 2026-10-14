package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.dto.UserForm;
import com.example.rbac.entity.SysUser;
import com.example.rbac.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:user:list')")
    public Result<Page<SysUser>> page(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) Integer status) {
        return Result.success(userService.page(pageNum, pageSize, username, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:user:query')")
    public Result<SysUser> detail(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    /**
     * 查询用户已分配的角色 ID
     */
    @GetMapping("/{id}/roles")
    @PreAuthorize("@ss.hasPerm('system:user:query')")
    public Result<List<Long>> roles(@PathVariable Long id) {
        return Result.success(userService.getRoleIds(id));
    }

    /**
     * 查询用户已分配的岗位 ID
     */
    @GetMapping("/{id}/posts")
    @PreAuthorize("@ss.hasPerm('system:user:query')")
    public Result<List<Long>> posts(@PathVariable Long id) {
        return Result.success(userService.getPostIds(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:user:add')")
    @OperLog(module = "用户管理", action = AuditAction.CREATE, description = "新增用户")
    public Result<Void> create(@Valid @RequestBody UserForm form) {
        userService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    @OperLog(module = "用户管理", action = AuditAction.UPDATE, description = "修改用户")
    public Result<Void> update(@Valid @RequestBody UserForm form) {
        userService.update(form);
        return Result.success("修改成功", null);
    }

    /**
     * 设置用户状态：1 在职 / 0 停用 / 2 离职。
     * 停用与离职都会立即失效其登录凭证，把已登录用户踢下线。
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        String message;
        if (status != null && status == 2) {
            message = "已标记离职并失效其登录状态";
        } else if (status != null && status == 0) {
            message = "已停用并失效其登录状态";
        } else {
            message = "已启用";
        }
        return Result.success(message, null);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@ss.hasPerm('system:user:role')")
    @OperLog(module = "用户管理", action = AuditAction.GRANT, description = "分配用户角色")
    public Result<Void> updateRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.updateRoles(id, roleIds);
        return Result.success("分配成功", null);
    }

    @PutMapping("/{id}/posts")
    @PreAuthorize("@ss.hasPerm('system:user:edit')")
    @OperLog(module = "用户管理", action = AuditAction.GRANT, description = "分配用户岗位")
    public Result<Void> updatePosts(@PathVariable Long id, @RequestBody List<Long> postIds) {
        userService.updatePosts(id, postIds);
        return Result.success("分配成功", null);
    }

    @PutMapping("/{id}/resetPassword")
    @PreAuthorize("@ss.hasPerm('system:user:resetPwd')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String password) {
        userService.resetPassword(id, password);
        return Result.success("密码已重置", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:user:remove')")
    @OperLog(module = "用户管理", action = AuditAction.DELETE, description = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }
}
