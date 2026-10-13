package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.RoleForm;
import com.example.rbac.entity.SysRole;
import com.example.rbac.entity.SysRoleMenu;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysRoleMapper;
import com.example.rbac.mapper.SysRoleMenuMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;

    public Page<SysRole> page(long pageNum, long pageSize, String roleName) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .orderByAsc(SysRole::getSort);
        return roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public List<SysRole> listAll() {
        return roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1).orderByAsc(SysRole::getSort));
    }

    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(RoleForm form) {
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, form.getRoleKey()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.ROLE_KEY_EXISTS);
        }
        SysRole role = new SysRole();
        role.setRoleName(form.getRoleName());
        role.setRoleKey(form.getRoleKey());
        role.setSort(form.getSort() == null ? 0 : form.getSort());
        role.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        role.setRemark(form.getRemark());
        roleMapper.insert(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(RoleForm form) {
        SysRole exist = getById(form.getId());
        if (!exist.getRoleKey().equals(form.getRoleKey())) {
            Long count = roleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, form.getRoleKey()));
            if (count != null && count > 0) {
                throw new BusinessException(ResultCode.ROLE_KEY_EXISTS);
            }
        }
        SysRole role = new SysRole();
        role.setId(form.getId());
        role.setRoleName(form.getRoleName());
        role.setRoleKey(form.getRoleKey());
        role.setSort(form.getSort());
        role.setStatus(form.getStatus());
        role.setRemark(form.getRemark());
        roleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = getById(id);
        if ("admin".equals(role.getRoleKey())) {
            throw new BusinessException("内置管理员角色不允许删除");
        }
        Long used = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ResultCode.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    /**
     * 查询角色已分配的菜单 ID（授权页回显）
     */
    public List<Long> getMenuIds(Long roleId) {
        return menuMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 动态修改角色权限（核心接口）
     * <p>
     * 只修改角色与菜单的映射关系，不触碰任何用户的 Token。
     * 由于用户权限在每次请求时实时联表查询，保存后该角色的用户
     * 在下一次请求即可使用新权限，无需重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleMenus(Long roleId, List<Long> menuIds) {
        SysRole role = getById(roleId);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenuMapper.insert(roleMenu);
            }
        }
        log.info("角色权限已更新: roleKey={}, menuCount={}", role.getRoleKey(),
                menuIds == null ? 0 : menuIds.size());
    }
}
