package com.example.rbac.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 上报给审计日志系统的增删改操作消息
 * <p>
 * 与 audit-backend 的 {@code com.example.audit.mq.AuditLogMessage} 字段一一对应，
 * 但两个服务独立部署，不做类共享；对端加字段时靠 {@code ignoreUnknown} 兼容。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源系统：rbac / marketing / order-center */
    private String source;

    private String module;

    /** CREATE / UPDATE / DELETE / GRANT */
    private String action;

    private String description;

    private Long operatorId;

    private String operatorName;

    private String httpMethod;

    /** 请求路径，不含查询串 */
    private String requestUri;

    /** 接口请求参数 JSON（已脱敏、已截断） */
    private String requestParams;

    /** 1成功 0失败 */
    private Integer success;

    private String errorMsg;

    private Long durationMs;

    private String ip;

    /** 事件时间（毫秒） */
    private Long timestamp;
}
