package com.example.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.OperLog;
import com.example.rbac.common.Result;
import com.example.rbac.security.SensitiveOperation;
import com.example.rbac.dto.RoleForm;
import com.example.rbac.dto.RoleSubsystemMenusForm;
import com.example.rbac.entity.SysRole;
import com.example.rbac.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('system:role:list')")
    public Result<Page<SysRole>> page(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String roleName) {
        return Result.success(roleService.page(pageNum, pageSize, roleName));
    }

    /**
     * 全部启用角色（用户分配角色下拉框使用）
     */
    @GetMapping("/all")
    public Result<List<SysRole>> all() {
        return Result.success(roleService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:role:query')")
    public Result<SysRole> detail(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    /**
     * 查询角色已分配的菜单 ID（授权页回显勾选状态）
     */
    @GetMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('system:role:menu')")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.success(roleService.getMenuIds(id));
    }

    @SensitiveOperation
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:role:add')")
    @OperLog(module = "角色管理", action = AuditAction.CREATE, description = "新增角色")
    public Result<Void> create(@Valid @RequestBody RoleForm form) {
        roleService.create(form);
        return Result.success("新增成功", null);
    }

    @SensitiveOperation
    @PutMapping
    @PreAuthorize("@ss.hasPerm('system:role:edit')")
    @OperLog(module = "角色管理", action = AuditAction.UPDATE, description = "修改角色")
    public Result<Void> update(@Valid @RequestBody RoleForm form) {
        roleService.update(form);
        return Result.success("修改成功", null);
    }

    /**
     * 动态修改角色权限（核心接口）
     * 保存后该角色的用户下一次请求即生效，无需重新登录。
     */
    @SensitiveOperation
    @PutMapping("/{id}/menus")
    @PreAuthorize("@ss.hasPerm('system:role:menu')")
    @OperLog(module = "角色管理", action = AuditAction.GRANT, description = "分配角色权限")
    public Result<Void> updateRoleMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.updateRoleMenus(id, menuIds);
        return Result.success("权限已更新，对该角色用户立即生效", null);
    }

    @SensitiveOperation
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:role:remove')")
    @OperLog(module = "角色管理", action = AuditAction.DELETE, description = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 查询角色在指定子系统下已分配的菜单 ID（子系统授权页回显勾选状态）
     */
    @GetMapping("/{id}/subsystem-menus")
    @PreAuthorize("@ss.hasPerm('system:role:menu')")
    public Result<List<Long>> subsystemMenuIds(@PathVariable Long id, @RequestParam Long subsystemId) {
        return Result.success(roleService.getSubsystemMenuIds(id, subsystemId));
    }

    /**
     * 分配角色的子系统菜单（按子系统局部替换，不影响其他子系统）
     * <p>
     * 与 {@code PUT /role/{id}/menus} 的区别：那是本系统后台的菜单权限（影响 @PreAuthorize 判定），
     * 这里是下发给子系统的导航菜单，不参与本系统鉴权。
     */
    @SensitiveOperation
    @PutMapping("/{id}/subsystem-menus")
    @PreAuthorize("@ss.hasPerm('system:role:menu')")
    @OperLog(module = "角色管理", action = AuditAction.GRANT, description = "分配子系统菜单")
    public Result<Void> updateRoleSubsystemMenus(@PathVariable Long id,
                                                @Valid @RequestBody RoleSubsystemMenusForm form) {
        roleService.updateRoleSubsystemMenus(id, form.getSubsystemId(), form.getMenuIds());
        return Result.success("子系统菜单已更新", null);
    }
}
