package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 积分任务表单
 */
@Data
public class PointsTaskForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotBlank(message = "任务编码不能为空")
    private String taskCode;

    private String taskType;

    @NotNull(message = "积分不能为空")
    private Integer points;

    @NotNull(message = "每日上限不能为空")
    private Integer dailyLimit;

    private Integer status;

    private String remark;
}
