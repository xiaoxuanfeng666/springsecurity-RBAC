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
 * 操作日志
 * <p>
 * 只追加不修改，因此没有逻辑删除字段。写入类操作（新增/修改/删除/授权）由
 * {@link com.example.rbac.aspect.OperLogAspect} 自动记录，登录/下线/改密由业务代码直接记录。
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块，如 用户管理 */
    private String module;

    /** 动作，取值见 {@link com.example.rbac.common.AuditAction} */
    private String action;

    /** 操作说明，如 新增用户 */
    private String description;

    /** 目标用户（非用户类操作时为空） */
    private Long userId;

    private String username;

    /** 操作人（本人操作时与目标用户一致） */
    private Long operatorId;

    private String operatorName;

    private String httpMethod;

    /** 请求路径，不含查询串 —— 避免把密码等参数带进日志 */
    private String requestUri;

    /** 是否成功：1成功 0失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long durationMs;

    private String ip;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
