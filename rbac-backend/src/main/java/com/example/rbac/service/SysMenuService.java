package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.MenuForm;
import com.example.rbac.entity.SysMenu;
import com.example.rbac.entity.SysRoleMenu;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysRoleMenuMapper;
import com.example.rbac.util.MenuTreeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /**
     * 全部菜单树（菜单管理页 / 角色授权树）
     */
    public List<SysMenu> tree() {
        List<SysMenu> menus = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getParentId, SysMenu::getSort));
        return MenuTreeUtils.buildTree(menus);
    }

    public SysMenu getById(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(MenuForm form) {
        SysMenu menu = new SysMenu();
        copy(form, menu);
        menuMapper.insert(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MenuForm form) {
        getById(form.getId());
        if (form.getId().equals(form.getParentId())) {
            throw new BusinessException("父菜单不能是自己");
        }
        SysMenu menu = new SysMenu();
        menu.setId(form.getId());
        copy(form, menu);
        menuMapper.updateById(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        Long children = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ResultCode.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    private void copy(MenuForm form, SysMenu menu) {
        menu.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        menu.setMenuName(form.getMenuName());
        menu.setPath(form.getPath());
        menu.setComponent(form.getComponent());
        menu.setPerms(form.getPerms());
        menu.setType(form.getType());
        menu.setIcon(form.getIcon());
        menu.setSort(form.getSort() == null ? 0 : form.getSort());
        menu.setVisible(form.getVisible() == null ? 1 : form.getVisible());
        menu.setStatus(form.getStatus() == null ? 1 : form.getStatus());
    }
}
