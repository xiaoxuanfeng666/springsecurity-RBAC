package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录/下线审计日志
 * <p>
 * 只追加不修改，因此没有逻辑删除字段。
 */
@Data
@TableName("sys_login_audit")
public class SysLoginAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标用户 */
    private Long userId;

    private String username;

    /** 动作，取值见 {@link com.example.rbac.common.AuditAction} */
    private String action;

    /** 操作人（本人操作或系统触发时与目标用户一致） */
    private Long operatorId;

    private String operatorName;

    /** 说明 */
    private String reason;

    /** 来源IP */
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
