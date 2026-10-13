package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_post")
public class SysPost implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位编码 */
    private String postCode;

    /** 岗位名称 */
    private String postName;

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
