package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外部通讯录推送事件（幂等记录）
 */
@Data
@TableName("sys_directory_event")
public class SysDirectoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_RECEIVED = 1;
    public static final int STATUS_PROCESSED = 2;
    public static final int STATUS_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String eventId;

    private String eventType;

    private String payload;

    private Integer status;

    private String errorMsg;

    private LocalDateTime receivedTime;

    private LocalDateTime processedTime;
}
