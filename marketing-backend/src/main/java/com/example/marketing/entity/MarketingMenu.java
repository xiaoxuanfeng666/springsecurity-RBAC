package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.marketing.util.TreeNode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单 / 权限点（同步表）
 * <p>
 * 由 RBAC 经 MQ 下发后重建。同时承载两件事：
 * 目录(1)与菜单(2)是导航，按钮(3)是权限点，三者的 perms 都参与本系统的接口鉴权。
 */
@Data
@TableName("marketing_menu")
public class MarketingMenu implements Serializable, TreeNode<MarketingMenu> {

    private static final long serialVersionUID = 1L;

    /** 沿用 RBAC 的 sys_subsystem_menu.id */
    @TableId(type = IdType.INPUT)
    private Long id;

    private Long parentId;

    private String menuName;

    /** 1目录 2菜单 3按钮 */
    private Integer type;

    /** 权限标识，如 marketing:activity:add */
    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    /** 1显示 0隐藏 */
    private Integer visible;

    /** 1启用 0禁用 */
    private Integer status;

    private LocalDateTime syncTime;

    @TableField(exist = false)
    private List<MarketingMenu> children = new ArrayList<>();
}
