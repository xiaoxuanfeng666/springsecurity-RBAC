package com.example.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rbac.entity.SysSubsystemMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysSubsystemMenuMapper extends BaseMapper<SysSubsystemMenu> {

    /**
     * 查询角色在指定子系统下已分配的菜单 ID（授权页回显）
     * <p>
     * 必须按子系统过滤：sys_role_subsystem_menu 只存菜单 ID，
     * 不加子系统条件会把该角色在其他子系统的勾选一起带出来。
     */
    @Select("SELECT rsm.subsystem_menu_id FROM sys_role_subsystem_menu rsm " +
            "JOIN sys_subsystem_menu sm ON rsm.subsystem_menu_id = sm.id " +
            "WHERE rsm.role_id = #{roleId} AND sm.subsystem_id = #{subsystemId}")
    List<Long> selectMenuIdsByRoleIdAndSubsystem(@Param("roleId") Long roleId,
                                                 @Param("subsystemId") Long subsystemId);

    /**
     * 某子系统下的全部菜单 ID
     * <p>
     * 供超级管理员的授权树回显，以及按子系统清理授权时确定范围。
     */
    @Select("SELECT id FROM sys_subsystem_menu WHERE subsystem_id = #{subsystemId}")
    List<Long> selectMenuIdsBySubsystem(@Param("subsystemId") Long subsystemId);
}
