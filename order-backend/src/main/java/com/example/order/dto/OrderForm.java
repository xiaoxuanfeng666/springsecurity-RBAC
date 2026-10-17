package com.example.order.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表单
 * <p>
 * 字段长度/精度/取值范围都与 order_info 的列定义对齐。不这么做的话，
 * 超长或越界的值会一路走到 INSERT，被 MySQL 以「Data too long / Out of range value」拒绝，
 * 变成 500「系统异常」—— 用户看不出是自己填错了哪个字段。
 */
@Data
public class OrderForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "订单编号不能为空")
    @Size(max = 64, message = "订单编号长度不能超过 64")
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 64, message = "客户名称长度不能超过 64")
    private String customerName;

    /** order_amount 是 DECIMAL(10,2)：最多 8 位整数 + 2 位小数 */
    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0", message = "订单金额不能为负数")
    @Digits(integer = 8, fraction = 2, message = "订单金额最多 8 位整数、2 位小数")
    private BigDecimal orderAmount;

    /** 1待支付 2已支付 3已发货 4已完成 5已取消（status 列是 TINYINT，不加限制则 300 也能落库） */
    @Min(value = 1, message = "订单状态不合法")
    @Max(value = 5, message = "订单状态不合法")
    private Integer status;

    private LocalDateTime orderTime;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
