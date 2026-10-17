package com.example.audit.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 各系统经 MQ 上报的审计日志消息
 * <p>
 * 与 rbac-backend / marketing-backend / order-backend 里的同名 DTO 字段一一对应，
 * 但各服务独立部署、不做类共享：对端加字段时靠 {@code ignoreUnknown} 兼容。
 * <p>
 * 只承载增删改（CREATE/UPDATE/DELETE/GRANT）操作，不包含登录/登出。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogMessage implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 事件时间（毫秒） */
    private Long timestamp;
}
