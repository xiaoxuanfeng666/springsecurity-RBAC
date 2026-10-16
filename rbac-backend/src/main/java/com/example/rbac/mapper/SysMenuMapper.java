package com.example.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rbac.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户拥有的全部权限标识（动态权限核心：每次请求实时查询，改角色权限后立即生效）
     */
    @Select("SELECT DISTINCT m.perms FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND r.status = 1 AND r.del_flag = 0 " +
            "AND m.perms IS NOT NULL AND m.perms <> ''")
    List<String> selectPermsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户可见的目录与菜单（type 1/2），用于前端动态路由与侧边栏
     */
    @Select("SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.path, m.component, m.perms, " +
            "m.type, m.icon, m.sort, m.visible, m.status " +
            "FROM sys_menu m " +
            "JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.type IN (1, 2) " +
            "AND r.status = 1 AND r.del_flag = 0 " +
            "ORDER BY m.parent_id, m.sort")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /**
     * 查询全部启用的目录与菜单（type 1/2）
     * <p>
     * 供超级管理员使用：它的权限不落库，菜单也就不能按 sys_role_menu 过滤。
     */
    @Select("SELECT id, parent_id, menu_name, path, component, perms, " +
            "type, icon, sort, visible, status " +
            "FROM sys_menu WHERE status = 1 AND type IN (1, 2) " +
            "ORDER BY parent_id, sort")
    List<SysMenu> selectAllEnabledMenus();

    /**
     * 查询角色已分配的菜单 ID 列表，用于角色授权页回显
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 全部菜单 ID（含按钮、含禁用）
     * <p>
     * 供超级管理员的授权树回显：它没有 sys_role_menu 记录，按"全选"呈现才与实际情况一致。
     */
    @Select("SELECT id FROM sys_menu")
    List<Long> selectAllMenuIds();
}
