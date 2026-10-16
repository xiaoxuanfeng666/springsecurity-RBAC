package com.example.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.common.BusinessException;
import com.example.order.common.ResultCode;
import com.example.order.dto.OrderForm;
import com.example.order.dto.OrderStatView;
import com.example.order.dto.OrderStatusCount;
import com.example.order.entity.OrderInfo;
import com.example.order.mapper.OrderInfoMapper;
import com.example.order.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** order_no 列长度 */
    private static final int ORDER_NO_MAX_LENGTH = 64;

    private final OrderInfoMapper orderInfoMapper;

    public Page<OrderInfo> page(long pageNum, long pageSize, String orderNo, String customerName, Integer status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .like(StringUtils.hasText(orderNo), OrderInfo::getOrderNo, orderNo)
                .like(StringUtils.hasText(customerName), OrderInfo::getCustomerName, customerName)
                .eq(status != null, OrderInfo::getStatus, status)
                .orderByDesc(OrderInfo::getId);
        return orderInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public OrderInfo getById(Long id) {
        OrderInfo order = orderInfoMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_EXIST);
        }
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(OrderForm form) {
        if (orderNoExists(form.getOrderNo(), null)) {
            throw new BusinessException(ResultCode.ORDER_NO_EXISTS);
        }
        OrderInfo order = new OrderInfo();
        copy(form, order);
        orderInfoMapper.insert(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(OrderForm form) {
        OrderInfo exist = getById(form.getId());
        if (!exist.getOrderNo().equals(form.getOrderNo())
                && orderNoExists(form.getOrderNo(), form.getId())) {
            throw new BusinessException(ResultCode.ORDER_NO_EXISTS);
        }
        OrderInfo order = new OrderInfo();
        order.setId(form.getId());
        copy(form, order);
        orderInfoMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrderInfo order = getById(id);
        releaseOrderNo(order);
        orderInfoMapper.deleteById(id);
    }

    /**
     * 订单统计：总数、金额合计、按状态分布
     */
    public OrderStatView stat() {
        List<OrderStatusCount> statusCounts = orderInfoMapper.selectStatusCount();

        long totalCount = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderStatusCount row : statusCounts) {
            totalCount += row.getCount() == null ? 0L : row.getCount();
            totalAmount = totalAmount.add(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
        }

        OrderStatView view = new OrderStatView();
        view.setTotalCount(totalCount);
        view.setTotalAmount(totalAmount);
        view.setStatusCounts(statusCounts);
        return view;
    }

    private boolean orderNoExists(String orderNo, Long excludeId) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getOrderNo, orderNo)
                .ne(excludeId != null, OrderInfo::getId, excludeId);
        Long count = orderInfoMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 腾出 order_no 唯一键：删除是逻辑删除，行仍在表里、唯一键仍被占用，
     * 不改名的话这个订单编号就永远无法再使用
     */
    private void releaseOrderNo(OrderInfo order) {
        OrderInfo renamed = new OrderInfo();
        renamed.setId(order.getId());
        renamed.setOrderNo(UniqueKeyUtils.release(
                order.getOrderNo(), order.getId(), ORDER_NO_MAX_LENGTH));
        orderInfoMapper.updateById(renamed);
    }

    private void copy(OrderForm form, OrderInfo order) {
        order.setOrderNo(form.getOrderNo());
        order.setCustomerName(form.getCustomerName());
        order.setOrderAmount(form.getOrderAmount());
        order.setStatus(form.getStatus() == null ? OrderInfo.STATUS_UNPAID : form.getStatus());
        order.setOrderTime(form.getOrderTime() == null ? LocalDateTime.now() : form.getOrderTime());
        order.setRemark(form.getRemark());
    }
}
