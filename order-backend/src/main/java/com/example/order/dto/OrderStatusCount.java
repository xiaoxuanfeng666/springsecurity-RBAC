package com.example.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 某个订单状态的汇总行（数据库 GROUP BY 的结果）
 */
@Data
public class OrderStatusCount implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer status;

    private Long count;

    private BigDecimal amount;
}
