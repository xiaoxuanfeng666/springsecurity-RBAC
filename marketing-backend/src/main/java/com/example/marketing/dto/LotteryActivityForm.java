package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抽奖活动表单
 */
@Data
public class LotteryActivityForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "活动名称不能为空")
    private String activityName;

    @NotBlank(message = "活动编码不能为空")
    private String activityCode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotNull(message = "每日抽奖上限不能为空")
    private Integer dailyLimit;

    private String prizeDesc;

    private Integer status;

    private String remark;
}
