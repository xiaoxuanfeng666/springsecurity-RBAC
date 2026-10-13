package com.example.rbac.util;

import com.example.rbac.entity.SysMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MenuTreeUtils {

    private MenuTreeUtils() {
    }

    /**
     * 将扁平菜单列表组装为树。
     * 父节点不在列表中的节点会被提升为顶级，避免权限不全时菜单丢失。
     */
    public static List<SysMenu> buildTree(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, SysMenu> idMap = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity(), (a, b) -> a));

        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            Long parentId = menu.getParentId();
            SysMenu parent = parentId == null ? null : idMap.get(parentId);
            if (parent == null || parent.getId().equals(menu.getId())) {
                roots.add(menu);
            } else {
                parent.getChildren().add(menu);
            }
        }
        sort(roots);
        return roots;
    }

    private static void sort(List<SysMenu> menus) {
        menus.sort(Comparator.comparing(m -> m.getSort() == null ? 0 : m.getSort()));
        for (SysMenu menu : menus) {
            if (!menu.getChildren().isEmpty()) {
                sort(menu.getChildren());
            }
        }
    }
}
