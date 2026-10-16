package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动-奖品关联
 * <p>
 * 决定某个活动投哪些奖品、每个奖品的中奖概率与投放数量。
 * 概率之和允许小于 100，差额即为不中奖的概率。
 */
@Data
@TableName("lottery_activity_prize")
public class LotteryActivityPrize implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long prizeId;

    /** 中奖概率(%) */
    private BigDecimal probability;

    /** 该活动投放数量 */
    private Integer stock;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
