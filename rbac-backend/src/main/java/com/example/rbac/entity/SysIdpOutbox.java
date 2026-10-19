package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外部通讯录出站推送 Outbox
 */
@Data
@TableName("sys_idp_outbox")
public class SysIdpOutbox implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    public static final String TARGET_USER = "USER";
    public static final String TARGET_DEPT = "DEPT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String operation;

    private String targetType;

    private Long targetId;

    private String payload;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
