package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分任务配置（本系统业务表，与 RBAC 无关）
 */
@Data
@TableName("points_task")
public class PointsTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    /** 任务编码，唯一 */
    private String taskCode;

    /** daily 每日 / once 一次性 */
    private String taskType;

    /** 完成可得积分 */
    private Integer points;

    /** 每人每日完成次数上限 */
    private Integer dailyLimit;

    /** 1启用 0停用 */
    private Integer status;

    private String remark;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
