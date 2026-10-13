package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.rbac.util.TreeNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("sys_dept")
public class SysDept implements Serializable, TreeNode<SysDept> {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String deptName;

    /** 类型：1公司 2分公司 3部门 */
    private Integer type;

    private Integer sort;

    private String leader;

    private String phone;

    private String email;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 子部门，非数据库字段 */
    @TableField(exist = false)
    private List<SysDept> children = new ArrayList<>();
}
