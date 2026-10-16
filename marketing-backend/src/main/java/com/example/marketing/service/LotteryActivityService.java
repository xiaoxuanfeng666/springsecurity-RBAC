package com.example.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import com.example.marketing.dto.ActivityPrizeItem;
import com.example.marketing.dto.ActivityPrizeView;
import com.example.marketing.dto.LotteryActivityForm;
import com.example.marketing.entity.LotteryActivity;
import com.example.marketing.entity.LotteryActivityPrize;
import com.example.marketing.entity.LotteryPrize;
import com.example.marketing.mapper.LotteryActivityMapper;
import com.example.marketing.mapper.LotteryActivityPrizeMapper;
import com.example.marketing.mapper.LotteryPrizeMapper;
import com.example.marketing.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抽奖活动配置（含活动与奖品的关联）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryActivityService {

    /** 概率上限：百分比 */
    private static final BigDecimal MAX_PROBABILITY = new BigDecimal("100");

    /** activity_code 列长度 */
    private static final int ACTIVITY_CODE_MAX_LENGTH = 64;

    private final LotteryActivityMapper activityMapper;
    private final LotteryPrizeMapper prizeMapper;
    private final LotteryActivityPrizeMapper activityPrizeMapper;

    public Page<LotteryActivity> page(long pageNum, long pageSize, String activityName, Integer status) {
        LambdaQueryWrapper<LotteryActivity> wrapper = new LambdaQueryWrapper<LotteryActivity>()
                .like(StringUtils.hasText(activityName), LotteryActivity::getActivityName, activityName)
                .eq(status != null, LotteryActivity::getStatus, status)
                .orderByDesc(LotteryActivity::getId);
        return activityMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public LotteryActivity getById(Long id) {
        LotteryActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_EXIST);
        }
        return activity;
    }

    /**
     * 全部活动（不分页）：给抽奖记录页的活动下拉用
     */
    public List<LotteryActivity> listAll() {
        return activityMapper.selectList(new LambdaQueryWrapper<LotteryActivity>()
                .orderByDesc(LotteryActivity::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(LotteryActivityForm form) {
        if (codeExists(form.getActivityCode(), null)) {
            throw new BusinessException(ResultCode.ACTIVITY_CODE_EXISTS);
        }
        LotteryActivity activity = new LotteryActivity();
        copy(form, activity);
        activityMapper.insert(activity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(LotteryActivityForm form) {
        LotteryActivity exist = getById(form.getId());
        if (!exist.getActivityCode().equals(form.getActivityCode())
                && codeExists(form.getActivityCode(), form.getId())) {
            throw new BusinessException(ResultCode.ACTIVITY_CODE_EXISTS);
        }
        LotteryActivity activity = new LotteryActivity();
        activity.setId(form.getId());
        copy(form, activity);
        activityMapper.updateById(activity);
    }

    /**
     * 删除活动：连带清掉它的奖品配置
     * <p>
     * 与"删除奖品"相反 —— 这里是关联的从属方，留着配置没有意义。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LotteryActivity activity = getById(id);
        releaseCode(activity);
        activityMapper.deleteById(id);
        activityPrizeMapper.delete(new LambdaQueryWrapper<LotteryActivityPrize>()
                .eq(LotteryActivityPrize::getActivityId, id));
    }

    /**
     * 某个活动的奖品配置视图：**全部启用奖品** + 该活动已配置的概率与数量
     * <p>
     * 返回全集而不是只返回已关联的，前端才能在一张表里同时做"勾选新增"与"取消关联"。
     */
    public List<ActivityPrizeView> prizeConfig(Long activityId) {
        getById(activityId);
        List<LotteryPrize> prizes = prizeMapper.selectList(new LambdaQueryWrapper<LotteryPrize>()
                .eq(LotteryPrize::getStatus, 1)
                .orderByAsc(LotteryPrize::getId));
        Map<Long, LotteryActivityPrize> configured = activityPrizeMapper.selectList(
                        new LambdaQueryWrapper<LotteryActivityPrize>()
                                .eq(LotteryActivityPrize::getActivityId, activityId))
                .stream().collect(Collectors.toMap(LotteryActivityPrize::getPrizeId, Function.identity(),
                        (a, b) -> a));

        List<ActivityPrizeView> views = new ArrayList<>(prizes.size());
        for (LotteryPrize prize : prizes) {
            ActivityPrizeView view = new ActivityPrizeView();
            view.setPrizeId(prize.getId());
            view.setPrizeName(prize.getPrizeName());
            view.setPrizeLevel(prize.getPrizeLevel());
            view.setPrizeType(prize.getPrizeType());
            view.setPrizeValue(prize.getPrizeValue());
            view.setPrizeStatus(prize.getStatus());
            view.setTotalStock(prize.getTotalStock());
            LotteryActivityPrize row = configured.get(prize.getId());
            view.setAssociated(row != null);
            if (row != null) {
                view.setProbability(row.getProbability());
                view.setStock(row.getStock());
            }
            views.add(view);
        }
        return views;
    }

    /**
     * 覆盖式保存活动的奖品配置
     * <p>
     * 整体替换而不是逐条 diff：弹窗里提交的就是该活动的完整配置，
     * 替换逻辑简单且不会漏掉"取消勾选"的项。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePrizes(Long activityId, List<ActivityPrizeItem> items) {
        getById(activityId);
        List<ActivityPrizeItem> targets = items == null ? new ArrayList<>() : items;
        validateProbability(targets);
        validatePrizesExist(targets);

        activityPrizeMapper.delete(new LambdaQueryWrapper<LotteryActivityPrize>()
                .eq(LotteryActivityPrize::getActivityId, activityId));
        int sort = 0;
        for (ActivityPrizeItem item : targets) {
            LotteryActivityPrize row = new LotteryActivityPrize();
            row.setActivityId(activityId);
            row.setPrizeId(item.getPrizeId());
            row.setProbability(item.getProbability() == null ? BigDecimal.ZERO : item.getProbability());
            row.setStock(item.getStock() == null ? 0 : item.getStock());
            row.setSort(item.getSort() == null ? sort : item.getSort());
            activityPrizeMapper.insert(row);
            sort++;
        }
        log.info("活动奖品配置已更新: activityId={}, prizeCount={}", activityId, targets.size());
    }

    /**
     * 概率校验：每项 0~100，合计不超过 100
     * <p>
     * 合计小于 100 是允许的 —— 差额即"不中奖"的概率；
     * 超过 100 则意味着必有奖品发不出去，属于配置错误。
     */
    private void validateProbability(List<ActivityPrizeItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ActivityPrizeItem item : items) {
            BigDecimal probability = item.getProbability() == null ? BigDecimal.ZERO : item.getProbability();
            if (probability.compareTo(BigDecimal.ZERO) < 0
                    || probability.compareTo(MAX_PROBABILITY) > 0) {
                throw new BusinessException(ResultCode.ACTIVITY_PRIZE_INVALID);
            }
            total = total.add(probability);
        }
        if (total.compareTo(MAX_PROBABILITY) > 0) {
            throw new BusinessException(ResultCode.ACTIVITY_PRIZE_INVALID);
        }
    }

    private void validatePrizesExist(List<ActivityPrizeItem> items) {
        if (items.isEmpty()) {
            return;
        }
        List<Long> prizeIds = items.stream().map(ActivityPrizeItem::getPrizeId).distinct()
                .collect(Collectors.toList());
        Long found = prizeMapper.selectCount(new LambdaQueryWrapper<LotteryPrize>()
                .in(LotteryPrize::getId, prizeIds));
        if (found == null || found != prizeIds.size()) {
            throw new BusinessException(ResultCode.PRIZE_NOT_EXIST);
        }
    }

    private boolean codeExists(String activityCode, Long excludeId) {
        LambdaQueryWrapper<LotteryActivity> wrapper = new LambdaQueryWrapper<LotteryActivity>()
                .eq(LotteryActivity::getActivityCode, activityCode)
                .ne(excludeId != null, LotteryActivity::getId, excludeId);
        Long count = activityMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 腾出 activity_code 唯一键：删除是逻辑删除，行仍在表里、唯一键仍被占用，
     * 不改名的话这个活动编码就永远无法再使用
     */
    private void releaseCode(LotteryActivity activity) {
        LotteryActivity renamed = new LotteryActivity();
        renamed.setId(activity.getId());
        renamed.setActivityCode(UniqueKeyUtils.release(
                activity.getActivityCode(), activity.getId(), ACTIVITY_CODE_MAX_LENGTH));
        activityMapper.updateById(renamed);
    }

    private void copy(LotteryActivityForm form, LotteryActivity activity) {
        activity.setActivityName(form.getActivityName());
        activity.setActivityCode(form.getActivityCode());
        activity.setStartTime(form.getStartTime());
        activity.setEndTime(form.getEndTime());
        activity.setDailyLimit(form.getDailyLimit() == null ? 1 : form.getDailyLimit());
        activity.setPrizeDesc(form.getPrizeDesc());
        activity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        activity.setRemark(form.getRemark());
    }
}
