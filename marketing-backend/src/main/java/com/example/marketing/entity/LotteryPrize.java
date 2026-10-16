package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 奖品配置
 * <p>
 * 奖品的「档案」：名称、等级、类型、面值、总库存。
 * 不含概率与活动投放量 —— 那些属于「活动-奖品关联」，同一个奖品可被多个活动以不同概率复用。
 */
@Data
@TableName("lottery_prize")
public class LotteryPrize implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String prizeName;

    /** 一等奖 / 二等奖 / 谢谢参与 ... */
    private String prizeLevel;

    /** GOODS 实物 / COUPON 优惠券 / POINTS 积分 / NONE 谢谢参与 */
    private String prizeType;

    /** 奖品面值：优惠券金额或积分值，实物为空 */
    private Integer prizeValue;

    private Integer totalStock;

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
