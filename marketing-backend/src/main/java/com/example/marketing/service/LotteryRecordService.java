package com.example.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.entity.LotteryRecord;
import com.example.marketing.mapper.LotteryRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 抽奖记录查询（只读）
 * <p>
 * 本期不实现真实抽奖动作，因此没有写入接口，数据由 marketing-schema.sql 的演示数据提供。
 */
@Service
@RequiredArgsConstructor
public class LotteryRecordService {

    private final LotteryRecordMapper recordMapper;

    public Page<LotteryRecord> page(long pageNum, long pageSize, Long activityId, String username, Integer win,
                                    LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<LotteryRecord> wrapper = new LambdaQueryWrapper<LotteryRecord>()
                .eq(activityId != null, LotteryRecord::getActivityId, activityId)
                .like(StringUtils.hasText(username), LotteryRecord::getUsername, username)
                .eq(win != null, LotteryRecord::getWin, win)
                .ge(beginTime != null, LotteryRecord::getDrawTime, beginTime)
                .le(endTime != null, LotteryRecord::getDrawTime, endTime)
                .orderByDesc(LotteryRecord::getDrawTime);
        return recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
