package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动奖品配置的入参项：保存时整体替换该活动的奖品配置
 */
@Data
public class ActivityPrizeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "奖品ID不能为空")
    private Long prizeId;

    /** 中奖概率(%)，为空按 0 处理 */
    private BigDecimal probability;

    private Integer stock;

    private Integer sort;
}
