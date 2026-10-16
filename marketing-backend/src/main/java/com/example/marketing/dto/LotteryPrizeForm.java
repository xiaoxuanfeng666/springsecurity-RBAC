package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 奖品表单
 */
@Data
public class LotteryPrizeForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "奖品名称不能为空")
    private String prizeName;

    @NotBlank(message = "奖品等级不能为空")
    private String prizeLevel;

    @NotBlank(message = "奖品类型不能为空")
    private String prizeType;

    /** 面值：优惠券金额或积分值，实物可空 */
    private Integer prizeValue;

    private Integer totalStock;

    private Integer status;

    private String remark;
}
