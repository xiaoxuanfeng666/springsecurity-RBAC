package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String menuName;

    private String path;

    private String component;

    /** 权限标识，如 system:user:list */
    private String perms;

    /** 类型：1目录 2菜单 3按钮 */
    private Integer type;

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
    private List<SysMenu> children = new ArrayList<>();
}
