package com.example.marketing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.Result;
import com.example.marketing.dto.ActivityPrizeItem;
import com.example.marketing.dto.ActivityPrizeView;
import com.example.marketing.dto.LotteryActivityForm;
import com.example.marketing.entity.LotteryActivity;
import com.example.marketing.service.LotteryActivityService;
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
import java.util.List;

/**
 * 抽奖活动配置
 * <p>
 * 权限点由 RBAC 统一配置、经 MQ 下发，本系统只做判定。
 */
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class LotteryActivityController {

    private final LotteryActivityService activityService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('marketing:activity:list')")
    public Result<Page<LotteryActivity>> page(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "10") long pageSize,
                                              @RequestParam(required = false) String activityName,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(activityService.page(pageNum, pageSize, activityName, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:activity:query')")
    public Result<LotteryActivity> detail(@PathVariable Long id) {
        return Result.success(activityService.getById(id));
    }

    /**
     * 全部活动（不分页）：抽奖记录页按活动筛选时用
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPerm('marketing:activity:list', 'marketing:record:list')")
    public Result<List<LotteryActivity>> list() {
        return Result.success(activityService.listAll());
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('marketing:activity:add')")
    public Result<Void> create(@Valid @RequestBody LotteryActivityForm form) {
        activityService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('marketing:activity:edit')")
    public Result<Void> update(@Valid @RequestBody LotteryActivityForm form) {
        activityService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:activity:remove')")
    public Result<Void> delete(@PathVariable Long id) {
        activityService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 活动的奖品配置：全部启用奖品 + 该活动已配置的概率与数量
     */
    @GetMapping("/{id}/prizes")
    @PreAuthorize("@ss.hasAnyPerm('marketing:activity:query', 'marketing:activity:edit')")
    public Result<List<ActivityPrizeView>> prizes(@PathVariable Long id) {
        return Result.success(activityService.prizeConfig(id));
    }

    /**
     * 保存活动的奖品配置（整体替换）
     */
    @PutMapping("/{id}/prizes")
    @PreAuthorize("@ss.hasPerm('marketing:activity:edit')")
    public Result<Void> updatePrizes(@PathVariable Long id,
                                     @RequestBody List<ActivityPrizeItem> items) {
        activityService.updatePrizes(id, items);
        return Result.success("奖品配置已保存", null);
    }
}
