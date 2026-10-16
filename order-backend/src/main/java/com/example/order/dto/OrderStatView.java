package com.example.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单统计汇总
 */
@Data
public class OrderStatView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单总数（不含已逻辑删除） */
    private Long totalCount;

    /** 订单金额合计 */
    private BigDecimal totalAmount;

    /** 各状态的单数与金额，按状态升序 */
    private List<OrderStatusCount> statusCounts;
}
