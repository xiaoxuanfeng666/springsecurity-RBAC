package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.RoleForm;
import com.example.rbac.entity.SysRole;
import com.example.rbac.entity.SysRoleMenu;
import com.example.rbac.entity.SysRoleSubsystemMenu;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysRoleMapper;
import com.example.rbac.mapper.SysRoleMenuMapper;
import com.example.rbac.mapper.SysRoleSubsystemMenuMapper;
import com.example.rbac.mapper.SysSubsystemMenuMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.example.rbac.mq.SubsystemSyncService;
import com.example.rbac.security.PermissionSnapshotStore;
import com.example.rbac.security.SuperAdmin;
import com.example.rbac.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    /** sys_role.role_key 列长度 */
    private static final int ROLE_KEY_MAX_LENGTH = 64;

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleSubsystemMenuMapper roleSubsystemMenuMapper;
    private final SysSubsystemMenuMapper subsystemMenuMapper;
    private final SysSubsystemService subsystemService;
    private final PermissionSnapshotStore snapshotStore;
    private final SubsystemSyncService subsystemSyncService;

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
        if (SuperAdmin.ROLE_KEY.equals(role.getRoleKey())) {
            throw new BusinessException("内置管理员角色不允许删除");
        }
        Long used = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ResultCode.ROLE_IN_USE);
        }
        releaseRoleKey(role);
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    }

    /**
     * 腾出 role_key 唯一键：逻辑删除后行仍在表中，不改名同标识的角色就再也建不出来
     */
    private void releaseRoleKey(SysRole role) {
        SysRole renamed = new SysRole();
        renamed.setId(role.getId());
        renamed.setRoleKey(
                UniqueKeyUtils.release(role.getRoleKey(), role.getId(), ROLE_KEY_MAX_LENGTH));
        roleMapper.updateById(renamed);
    }

    /**
     * 查询角色已分配的菜单 ID（授权页回显）
     * <p>
     * 超级管理员的权限不落库（见 {@link com.example.rbac.security.SuperAdmin}），
     * 表中没有它的记录，因此按"全部菜单"回显 —— 这才是它实际的权限范围。
     */
    public List<Long> getMenuIds(Long roleId) {
        SysRole role = getById(roleId);
        if (SuperAdmin.ROLE_KEY.equals(role.getRoleKey())) {
            return menuMapper.selectAllMenuIds();
        }
        return menuMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 动态修改角色权限（核心接口）
     * <p>
     * 只修改角色与菜单的映射关系，不触碰任何用户的 Token。
     * 修改后失效该角色下所有用户的权限快照，用户在下一次请求即重建快照并拿到新权限，
     * 无需重新登录。
     * <p>
     * 超级管理员直接拒绝：它的权限来自代码里的通配符，写进表也不会被采用，
     * 允许保存只会让人以为改动生效了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleMenus(Long roleId, List<Long> menuIds) {
        SysRole role = getById(roleId);
        if (SuperAdmin.ROLE_KEY.equals(role.getRoleKey())) {
            throw new BusinessException(ResultCode.SUPER_ADMIN_NO_GRANT);
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                roleMenuMapper.insert(roleMenu);
            }
        }
        invalidateSnapshotsOfRole(roleId);
        log.info("角色权限已更新: roleKey={}, menuCount={}", role.getRoleKey(),
                menuIds == null ? 0 : menuIds.size());
    }

    /**
     * 失效该角色下所有用户的权限快照，使其下一次请求重建
     */
    private void invalidateSnapshotsOfRole(Long roleId) {
        List<Long> userIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
        snapshotStore.invalidateUsers(userIds);
    }

    /**
     * 查询角色在指定子系统下已分配的菜单 ID（授权页回显）
     * <p>
     * 超级管理员的授权不落库，按该子系统的"全部菜单"回显。
     */
    public List<Long> getSubsystemMenuIds(Long roleId, Long subsystemId) {
        SysRole role = getById(roleId);
        subsystemService.getById(subsystemId);
        if (SuperAdmin.ROLE_KEY.equals(role.getRoleKey())) {
            return subsystemMenuMapper.selectMenuIdsBySubsystem(subsystemId);
        }
        return subsystemMenuMapper.selectMenuIdsByRoleIdAndSubsystem(roleId, subsystemId);
    }

    /**
     * 修改角色在某个子系统下的菜单授权
     * <p>
     * 按子系统**局部替换**：只动这个子系统的勾选，其他子系统的授权保持不变。
     * <p>
     * 不动权限快照：子系统菜单是给子系统用的导航与权限点，不参与本系统的 {@code @PreAuthorize} 判定，
     * 变更后不需要让任何人重建快照；改动经 MQ 下发给对应子系统，由子系统自己刷新。
     * <p>
     * 超级管理员直接拒绝，理由同 {@link #updateRoleMenus}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleSubsystemMenus(Long roleId, Long subsystemId, List<Long> menuIds) {
        SysRole role = getById(roleId);
        if (SuperAdmin.ROLE_KEY.equals(role.getRoleKey())) {
            throw new BusinessException(ResultCode.SUPER_ADMIN_NO_GRANT);
        }
        subsystemService.getById(subsystemId);

        List<Long> scopedIds = subsystemMenuMapper.selectMenuIdsBySubsystem(subsystemId);

        if (!scopedIds.isEmpty()) {
            roleSubsystemMenuMapper.delete(new LambdaQueryWrapper<SysRoleSubsystemMenu>()
                    .eq(SysRoleSubsystemMenu::getRoleId, roleId)
                    .in(SysRoleSubsystemMenu::getSubsystemMenuId, scopedIds));
        }

        if (menuIds != null && !menuIds.isEmpty()) {
            // 过滤掉不属于该子系统的 ID：客户端传来的越界 ID 会插成一条永远读不出来的脏关联
            for (Long menuId : menuIds.stream().distinct().collect(Collectors.toList())) {
                if (!scopedIds.contains(menuId)) {
                    continue;
                }
                SysRoleSubsystemMenu row = new SysRoleSubsystemMenu();
                row.setRoleId(roleId);
                row.setSubsystemMenuId(menuId);
                roleSubsystemMenuMapper.insert(row);
            }
        }
        log.info("角色子系统菜单已更新: roleKey={}, subsystemId={}, menuCount={}",
                role.getRoleKey(), subsystemId, menuIds == null ? 0 : menuIds.size());
        subsystemSyncService.sync(subsystemId);
    }
}
