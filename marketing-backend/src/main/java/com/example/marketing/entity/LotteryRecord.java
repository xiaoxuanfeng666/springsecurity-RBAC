package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 抽奖记录（本系统业务表）
 * <p>
 * 本期只提供查询：真实的抽奖动作不在本期范围内，表内是演示数据。
 */
@Data
@TableName("lottery_record")
public class LotteryRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String activityName;

    private Long userId;

    private String username;

    private String prizeName;

    private String prizeLevel;

    /** 1中奖 0未中奖 */
    private Integer win;

    private LocalDateTime drawTime;
}
