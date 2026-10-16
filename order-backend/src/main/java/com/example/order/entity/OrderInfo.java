package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单（本系统业务表，与 RBAC 无关）
 * <p>
 * 表名用 {@code order_info} 而不是 {@code order}：后者是 MySQL 保留字，
 * 用了就得处处加反引号，得不偿失。
 */
@Data
@TableName("order_info")
public class OrderInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待支付 */
    public static final int STATUS_UNPAID = 1;
    /** 已支付 */
    public static final int STATUS_PAID = 2;
    /** 已发货 */
    public static final int STATUS_SHIPPED = 3;
    /** 已完成 */
    public static final int STATUS_FINISHED = 4;
    /** 已取消 */
    public static final int STATUS_CANCELLED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号，唯一 */
    private String orderNo;

    /** 客户名称，存文本即可：客户档案不归订单中心管 */
    private String customerName;

    private BigDecimal orderAmount;

    /** 1待支付 2已支付 3已发货 4已完成 5已取消 */
    private Integer status;

    private LocalDateTime orderTime;

    private String remark;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
