package com.example.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志
 * <p>
 * 只追加不修改：由各系统经 MQ 上报后落库，本系统不产生业务操作。
 * 只记录增删改（CREATE/UPDATE/DELETE/GRANT），不记录登录/登出。
 */
@Data
@TableName("audit_log")
public class AuditLogRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源系统：rbac / marketing / order-center */
    private String source;

    /** 业务模块，如 用户管理 */
    private String module;

    /** CREATE / UPDATE / DELETE / GRANT */
    private String action;

    /** 操作说明 */
    private String description;

    private Long operatorId;

    private String operatorName;

    private String httpMethod;

    /** 请求路径，不含查询串 */
    private String requestUri;

    /** 接口请求参数 JSON（生产端已脱敏、已截断） */
    private String requestParams;

    /** 1成功 0失败 */
    private Integer success;

    private String errorMsg;

    private Long durationMs;

    private String ip;

    /** 事件时间 */
    private LocalDateTime createTime;
}
