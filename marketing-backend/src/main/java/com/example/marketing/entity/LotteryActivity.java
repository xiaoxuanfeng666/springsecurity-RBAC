package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抽奖活动配置（本系统业务表，与 RBAC 无关）
 */
@Data
@TableName("lottery_activity")
public class LotteryActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String activityName;

    /** 活动编码，唯一 */
    private String activityCode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 每人每日抽奖次数上限 */
    private Integer dailyLimit;

    private String prizeDesc;

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
