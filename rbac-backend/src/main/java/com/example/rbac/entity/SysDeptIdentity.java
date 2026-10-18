package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外部部门与本地组织节点的映射
 */
@Data
@TableName("sys_dept_identity")
public class SysDeptIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String corpId;

    private String externalDeptId;

    private Long deptId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
