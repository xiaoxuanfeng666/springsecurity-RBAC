package com.example.marketing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.Result;
import com.example.marketing.entity.PointsFlow;
import com.example.marketing.service.PointsFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 积分流水记录（只读）
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsFlowController {

    private final PointsFlowService flowService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('marketing:points:list')")
    public Result<Page<PointsFlow>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(flowService.page(pageNum, pageSize, username, changeType, beginTime, endTime));
    }
}
