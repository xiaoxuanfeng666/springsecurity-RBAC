package com.example.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import com.example.marketing.dto.LotteryPrizeForm;
import com.example.marketing.entity.LotteryActivityPrize;
import com.example.marketing.entity.LotteryPrize;
import com.example.marketing.mapper.LotteryActivityPrizeMapper;
import com.example.marketing.mapper.LotteryPrizeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 奖品配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryPrizeService {

    private final LotteryPrizeMapper prizeMapper;
    private final LotteryActivityPrizeMapper activityPrizeMapper;

    public Page<LotteryPrize> page(long pageNum, long pageSize, String prizeName, Integer status) {
        LambdaQueryWrapper<LotteryPrize> wrapper = new LambdaQueryWrapper<LotteryPrize>()
                .like(StringUtils.hasText(prizeName), LotteryPrize::getPrizeName, prizeName)
                .eq(status != null, LotteryPrize::getStatus, status)
                .orderByAsc(LotteryPrize::getId);
        return prizeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public LotteryPrize getById(Long id) {
        LotteryPrize prize = prizeMapper.selectById(id);
        if (prize == null) {
            throw new BusinessException(ResultCode.PRIZE_NOT_EXIST);
        }
        return prize;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(LotteryPrizeForm form) {
        LotteryPrize prize = new LotteryPrize();
        copy(form, prize);
        prizeMapper.insert(prize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(LotteryPrizeForm form) {
        getById(form.getId());
        LotteryPrize prize = new LotteryPrize();
        prize.setId(form.getId());
        copy(form, prize);
        prizeMapper.updateById(prize);
    }

    /**
     * 删除奖品：被活动引用过的奖品不允许删
     * <p>
     * 活动的奖品配置是业务配置，静默把关联删掉会让活动在无人察觉的情况下少一个奖项。
     * 要先删就得先去各活动里取消配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getById(id);
        Long used = activityPrizeMapper.selectCount(new LambdaQueryWrapper<LotteryActivityPrize>()
                .eq(LotteryActivityPrize::getPrizeId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ResultCode.PRIZE_IN_USE);
        }
        prizeMapper.deleteById(id);
    }

    /**
     * 全部启用奖品：活动的「配置奖品」弹窗用
     */
    public List<LotteryPrize> listEnabled() {
        return prizeMapper.selectList(new LambdaQueryWrapper<LotteryPrize>()
                .eq(LotteryPrize::getStatus, 1)
                .orderByAsc(LotteryPrize::getId));
    }

    private void copy(LotteryPrizeForm form, LotteryPrize prize) {
        prize.setPrizeName(form.getPrizeName());
        prize.setPrizeLevel(form.getPrizeLevel());
        prize.setPrizeType(form.getPrizeType());
        prize.setPrizeValue(form.getPrizeValue());
        prize.setTotalStock(form.getTotalStock() == null ? 0 : form.getTotalStock());
        prize.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        prize.setRemark(form.getRemark());
    }
}
