package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 奖品表单
 * <p>
 * 字段长度与取值范围都与 lottery_prize 的列定义对齐，避免超长/越界的值
 * 走到 INSERT 才被 MySQL 拒绝、最终以 500「系统异常」回给前端。
 */
@Data
public class LotteryPrizeForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "奖品名称不能为空")
    @Size(max = 64, message = "奖品名称长度不能超过 64")
    private String prizeName;

    @NotBlank(message = "奖品等级不能为空")
    @Size(max = 32, message = "奖品等级长度不能超过 32")
    private String prizeLevel;

    @NotBlank(message = "奖品类型不能为空")
    @Size(max = 32, message = "奖品类型长度不能超过 32")
    private String prizeType;

    /** 面值：优惠券金额或积分值，实物可空 */
    private Integer prizeValue;

    private Integer totalStock;

    /** 1启用 0停用 */
    @Min(value = 0, message = "状态只能是 1启用 或 0停用")
    @Max(value = 1, message = "状态只能是 1启用 或 0停用")
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
