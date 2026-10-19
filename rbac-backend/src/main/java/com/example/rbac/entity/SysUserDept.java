package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户与部门关联（多部门）
 */
@Data
@TableName("sys_user_dept")
public class SysUserDept implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long deptId;

    /** 1 主部门 / 0 兼任部门 */
    private Integer isMain;

    /** 1 该部门负责人 / 0 否 */
    private Integer isLeader;

    private Integer orderInDept;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
