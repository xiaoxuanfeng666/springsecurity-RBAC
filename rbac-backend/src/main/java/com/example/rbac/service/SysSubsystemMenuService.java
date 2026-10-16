package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.SubsystemMenuForm;
import com.example.rbac.entity.SysRoleSubsystemMenu;
import com.example.rbac.entity.SysSubsystemMenu;
import com.example.rbac.mapper.SysRoleSubsystemMenuMapper;
import com.example.rbac.mapper.SysSubsystemMenuMapper;
import com.example.rbac.mq.SubsystemSyncService;
import com.example.rbac.util.TreeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 子系统菜单（下发给子系统的导航菜单与权限点）
 */
@Service
@RequiredArgsConstructor
public class SysSubsystemMenuService {

    private final SysSubsystemMenuMapper subsystemMenuMapper;
    private final SysRoleSubsystemMenuMapper roleSubsystemMenuMapper;
    private final SysSubsystemService subsystemService;
    private final SubsystemSyncService subsystemSyncService;

    /**
     * 指定子系统的菜单树（菜单管理页 / 角色授权树共用）
     */
    public List<SysSubsystemMenu> tree(Long subsystemId) {
        subsystemService.getById(subsystemId);
        List<SysSubsystemMenu> menus = subsystemMenuMapper.selectList(
                new LambdaQueryWrapper<SysSubsystemMenu>()
                        .eq(SysSubsystemMenu::getSubsystemId, subsystemId)
                        .orderByAsc(SysSubsystemMenu::getParentId, SysSubsystemMenu::getSort));
        return TreeUtils.buildTree(menus);
    }

    public SysSubsystemMenu getById(Long id) {
        SysSubsystemMenu menu = subsystemMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("子系统菜单不存在");
        }
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(SubsystemMenuForm form) {
        subsystemService.getById(form.getSubsystemId());
        SysSubsystemMenu menu = new SysSubsystemMenu();
        copy(form, menu);
        subsystemMenuMapper.insert(menu);
        subsystemSyncService.sync(form.getSubsystemId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SubsystemMenuForm form) {
        SysSubsystemMenu exist = getById(form.getId());
        subsystemService.getById(form.getSubsystemId());
        if (form.getId().equals(form.getParentId())) {
            throw new BusinessException("父菜单不能是自己");
        }
        // 不允许把菜单挪到别的子系统父节点下：父子必须同属一个子系统，
        // 否则该子树会在原系统里凭空消失（tree 按 subsystem_id 过滤）。
        if (!exist.getSubsystemId().equals(form.getSubsystemId())) {
            throw new BusinessException("不能修改菜单所属的子系统");
        }
        SysSubsystemMenu menu = new SysSubsystemMenu();
        menu.setId(form.getId());
        copy(form, menu);
        subsystemMenuMapper.updateById(menu);
        subsystemSyncService.sync(form.getSubsystemId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysSubsystemMenu menu = getById(id);
        Long children = subsystemMenuMapper.selectCount(new LambdaQueryWrapper<SysSubsystemMenu>()
                .eq(SysSubsystemMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.MENU_HAS_CHILDREN);
        }
        subsystemMenuMapper.deleteById(id);
        roleSubsystemMenuMapper.delete(new LambdaQueryWrapper<SysRoleSubsystemMenu>()
                .eq(SysRoleSubsystemMenu::getSubsystemMenuId, id));
        subsystemSyncService.sync(menu.getSubsystemId());
    }

    private void copy(SubsystemMenuForm form, SysSubsystemMenu menu) {
        menu.setSubsystemId(form.getSubsystemId());
        menu.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        menu.setMenuName(form.getMenuName());
        menu.setType(form.getType());
        menu.setPerms(form.getPerms());
        menu.setPath(form.getPath());
        menu.setComponent(form.getComponent());
        menu.setIcon(form.getIcon());
        menu.setSort(form.getSort() == null ? 0 : form.getSort());
        menu.setVisible(form.getVisible() == null ? 1 : form.getVisible());
        menu.setStatus(form.getStatus() == null ? 1 : form.getStatus());
    }
}
