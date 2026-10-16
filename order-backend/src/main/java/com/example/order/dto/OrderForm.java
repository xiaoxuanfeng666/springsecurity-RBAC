package com.example.order.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表单
 */
@Data
public class OrderForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0", message = "订单金额不能为负数")
    private BigDecimal orderAmount;

    /** 1待支付 2已支付 3已发货 4已完成 5已取消 */
    private Integer status;

    private LocalDateTime orderTime;

    private String remark;
}
