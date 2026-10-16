package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 子系统登记
 * <p>
 * RBAC 作为中枢统一给各子系统配菜单权限，本表记录有哪些子系统。
 */
@Data
@TableName("sys_subsystem")
public class SysSubsystem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 子系统编码，如 order-center */
    private String subsystemCode;

    /** 子系统名称 */
    private String subsystemName;

    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
