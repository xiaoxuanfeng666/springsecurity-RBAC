package com.example.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.order.entity.OrderMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMenuMapper extends BaseMapper<OrderMenu> {

    /**
     * 用户拥有的权限标识（本系统鉴权的唯一来源，由 RBAC 经 MQ 下发的三张表联查得到）
     */
    @Select("SELECT DISTINCT m.perms FROM order_menu m " +
            "JOIN order_role_menu rm ON m.id = rm.menu_id " +
            "JOIN order_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 " +
            "AND m.perms IS NOT NULL AND m.perms <> ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 用户可见的目录与菜单（type 1/2），用于前端动态路由与侧边栏
     */
    @Select("SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.type, m.perms, m.path, m.component, " +
            "m.icon, m.sort, m.visible, m.status " +
            "FROM order_menu m " +
            "JOIN order_role_menu rm ON m.id = rm.menu_id " +
            "JOIN order_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.type IN (1, 2) " +
            "ORDER BY m.parent_id, m.sort")
    List<OrderMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 全部启用的目录与菜单
     * <p>
     * 供超级管理员使用：它的授权不落库（RBAC 侧同样如此），菜单不能按角色过滤。
     */
    @Select("SELECT id, parent_id, menu_name, type, perms, path, component, icon, sort, visible, status " +
            "FROM order_menu WHERE status = 1 AND type IN (1, 2) " +
            "ORDER BY parent_id, sort")
    List<OrderMenu> selectAllEnabledMenus();
}
