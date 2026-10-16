package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.rbac.util.TreeNode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 子系统菜单
 * <p>
 * 与 {@link SysMenu} 完全独立：这里存的是「下发给子系统的导航菜单与权限点」，
 * 目录(1)/菜单(2)/按钮(3) 三级。下发给子系统后由子系统自己判定
 * （子系统的权限点是 marketing:activity:add 这种带子系统前缀的命名），
 * 因此不参与 RBAC 本系统的 {@code @PreAuthorize} 判定。
 */
@Data
@TableName("sys_subsystem_menu")
public class SysSubsystemMenu implements Serializable, TreeNode<SysSubsystemMenu> {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属子系统ID */
    private Long subsystemId;

    private Long parentId;

    private String menuName;

    /** 类型：1目录 2菜单 3按钮 */
    private Integer type;

    /** 权限标识，如 marketing:activity:add，下发给子系统后由子系统鉴权使用 */
    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    /** 是否显示：1显示 0隐藏 */
    private Integer visible;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 子菜单，非数据库字段 */
    @TableField(exist = false)
    private List<SysSubsystemMenu> children = new ArrayList<>();
}
