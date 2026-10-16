package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class SubsystemMenuForm {

    private Long id;

    @NotNull(message = "所属子系统不能为空")
    private Long subsystemId;

    /** 父菜单 ID，0 表示顶级 */
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    /** 类型：1目录 2菜单 3按钮 */
    @NotNull(message = "菜单类型不能为空")
    private Integer type;

    /** 权限标识，按钮类型必填 */
    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    private Integer visible;

    private Integer status;
}
