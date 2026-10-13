package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class MenuForm {

    private Long id;

    /** 父菜单 ID，0 表示顶级 */
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    private String path;

    private String component;

    private String perms;

    @NotNull(message = "菜单类型不能为空")
    private Integer type;

    private String icon;

    private Integer sort;

    private Integer visible;

    private Integer status;
}
