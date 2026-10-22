package com.example.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.order.common.OperLog;
import com.example.order.common.Result;
import com.example.order.security.SensitiveOperation;
import com.example.order.dto.OrderForm;
import com.example.order.dto.OrderStatView;
import com.example.order.entity.OrderInfo;
import com.example.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 订单管理
 * <p>
 * 权限点由 RBAC 统一配置、经 MQ 下发，本系统只做判定。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('order:order:list')")
    public Result<Page<OrderInfo>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestParam(required = false) String orderNo,
                                        @RequestParam(required = false) String customerName,
                                        @RequestParam(required = false) Integer status) {
        return Result.success(orderService.page(pageNum, pageSize, orderNo, customerName, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('order:order:query')")
    public Result<OrderInfo> detail(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('order:order:add')")
    @OperLog(module = "订单管理", action = "CREATE", description = "新增订单")
    public Result<Void> create(@Valid @RequestBody OrderForm form) {
        orderService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('order:order:edit')")
    @OperLog(module = "订单管理", action = "UPDATE", description = "修改订单")
    public Result<Void> update(@Valid @RequestBody OrderForm form) {
        orderService.update(form);
        return Result.success("修改成功", null);
    }

    @SensitiveOperation
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('order:order:remove')")
    @OperLog(module = "订单管理", action = "DELETE", description = "删除订单")
    public Result<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 订单统计（只读汇总）
     */
    @GetMapping("/stat")
    @PreAuthorize("@ss.hasPerm('order:stat:list')")
    public Result<OrderStatView> stat() {
        return Result.success(orderService.stat());
    }
}
