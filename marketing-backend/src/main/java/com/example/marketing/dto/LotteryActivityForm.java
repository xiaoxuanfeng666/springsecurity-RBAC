package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抽奖活动表单
 * <p>
 * 字段长度与取值范围都与 lottery_activity 的列定义对齐，避免超长/越界的值
 * 走到 INSERT 才被 MySQL 拒绝、最终以 500「系统异常」回给前端。
 */
@Data
public class LotteryActivityForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 64, message = "活动名称长度不能超过 64")
    private String activityName;

    @NotBlank(message = "活动编码不能为空")
    @Size(max = 64, message = "活动编码长度不能超过 64")
    private String activityCode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotNull(message = "每日抽奖上限不能为空")
    private Integer dailyLimit;

    @Size(max = 255, message = "奖品说明长度不能超过 255")
    private String prizeDesc;

    /** 1启用 0停用 */
    @Min(value = 0, message = "状态只能是 1启用 或 0停用")
    @Max(value = 1, message = "状态只能是 1启用 或 0停用")
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
