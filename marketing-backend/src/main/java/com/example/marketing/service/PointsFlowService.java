package com.example.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.entity.PointsFlow;
import com.example.marketing.mapper.PointsFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 积分流水查询（只读）
 * <p>
 * 本期不实现真实积分变动，因此没有写入接口，数据由 marketing-schema.sql 的演示数据提供。
 */
@Service
@RequiredArgsConstructor
public class PointsFlowService {

    private final PointsFlowMapper flowMapper;

    public Page<PointsFlow> page(long pageNum, long pageSize, String username, String changeType,
                                 LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<PointsFlow> wrapper = new LambdaQueryWrapper<PointsFlow>()
                .like(StringUtils.hasText(username), PointsFlow::getUsername, username)
                .eq(StringUtils.hasText(changeType), PointsFlow::getChangeType, changeType)
                .ge(beginTime != null, PointsFlow::getCreateTime, beginTime)
                .le(endTime != null, PointsFlow::getCreateTime, endTime)
                .orderByDesc(PointsFlow::getCreateTime);
        return flowMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
