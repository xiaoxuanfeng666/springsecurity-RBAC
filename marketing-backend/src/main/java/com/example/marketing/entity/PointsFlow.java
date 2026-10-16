package com.example.marketing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分流水记录（本系统业务表）
 * <p>
 * 本期只提供查询：真实的积分变动应由业务动作触发，不在本期范围内，
 * 因此表内是演示数据，没有对应的写入接口。
 */
@Data
@TableName("points_flow")
public class PointsFlow implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** TASK 完成积分任务 / LOTTERY 抽奖消耗 / ADMIN 人工调整 */
    private String changeType;

    /** 积分变动值，正数为增加、负数为扣减 */
    private Integer points;

    /** 变动后余额 */
    private Integer balance;

    private String source;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
