package com.example.marketing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.OperLog;
import com.example.marketing.common.Result;
import com.example.marketing.security.SensitiveOperation;
import com.example.marketing.dto.LotteryPrizeForm;
import com.example.marketing.entity.LotteryPrize;
import com.example.marketing.service.LotteryPrizeService;
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
 * 奖品配置
 */
@RestController
@RequestMapping("/prize")
@RequiredArgsConstructor
public class LotteryPrizeController {

    private final LotteryPrizeService prizeService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('marketing:prize:list')")
    public Result<Page<LotteryPrize>> page(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) String prizeName,
                                           @RequestParam(required = false) Integer status) {
        return Result.success(prizeService.page(pageNum, pageSize, prizeName, status));
    }

    /**
     * 全部启用奖品：活动页的「配置奖品」弹窗与下拉共用
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPerm('marketing:prize:list', 'marketing:activity:query', "
            + "'marketing:activity:add', 'marketing:activity:edit')")
    public Result<List<LotteryPrize>> list() {
        return Result.success(prizeService.listEnabled());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:prize:query')")
    public Result<LotteryPrize> detail(@PathVariable Long id) {
        return Result.success(prizeService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('marketing:prize:add')")
    @OperLog(module = "奖品管理", action = "CREATE", description = "新增奖品")
    public Result<Void> create(@Valid @RequestBody LotteryPrizeForm form) {
        prizeService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('marketing:prize:edit')")
    @OperLog(module = "奖品管理", action = "UPDATE", description = "修改奖品")
    public Result<Void> update(@Valid @RequestBody LotteryPrizeForm form) {
        prizeService.update(form);
        return Result.success("修改成功", null);
    }

    @SensitiveOperation
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:prize:remove')")
    @OperLog(module = "奖品管理", action = "DELETE", description = "删除奖品")
    public Result<Void> delete(@PathVariable Long id) {
        prizeService.delete(id);
        return Result.success("删除成功", null);
    }
}
