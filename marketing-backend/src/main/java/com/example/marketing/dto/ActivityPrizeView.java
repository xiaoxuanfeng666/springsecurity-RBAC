package com.example.marketing.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动奖品配置项
 * <p>
 * 用于活动的「配置奖品」弹窗：把该活动的全部奖品合成一张表返回，
 * 前端直接渲染成一排输入框，勾选即表示参与该活动。
 */
@Data
public class ActivityPrizeView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long prizeId;

    private String prizeName;

    private String prizeLevel;

    private String prizeType;

    private Integer prizeValue;

    /** 奖品自身的状态：停用的奖品不应再被配置到活动里 */
    private Integer prizeStatus;

    private Integer totalStock;

    /** 该活动下的中奖概率(%)，未关联时为空 */
    private BigDecimal probability;

    /** 该活动下的投放数量，未关联时为空 */
    private Integer stock;

    /** 是否已配置到该活动 */
    private Boolean associated;
}
