package com.example.marketing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.Result;
import com.example.marketing.entity.LotteryRecord;
import com.example.marketing.service.LotteryRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 抽奖记录（只读）
 */
@RestController
@RequestMapping("/record")
@RequiredArgsConstructor
public class LotteryRecordController {

    private final LotteryRecordService recordService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('marketing:record:list')")
    public Result<Page<LotteryRecord>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer win,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beginTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(recordService.page(pageNum, pageSize, activityId, username, win,
                beginTime, endTime));
    }
}
