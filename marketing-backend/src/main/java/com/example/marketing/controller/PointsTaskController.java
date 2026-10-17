package com.example.marketing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.OperLog;
import com.example.marketing.common.Result;
import com.example.marketing.entity.PointsTask;
import com.example.marketing.dto.PointsTaskForm;
import com.example.marketing.service.PointsTaskService;
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
 * 积分任务配置
 */
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class PointsTaskController {

    private final PointsTaskService taskService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('marketing:task:list')")
    public Result<Page<PointsTask>> page(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize,
                                         @RequestParam(required = false) String taskName,
                                         @RequestParam(required = false) Integer status) {
        return Result.success(taskService.page(pageNum, pageSize, taskName, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:task:query')")
    public Result<PointsTask> detail(@PathVariable Long id) {
        return Result.success(taskService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPerm('marketing:task:add')")
    @OperLog(module = "积分任务", action = "CREATE", description = "新增积分任务")
    public Result<Void> create(@Valid @RequestBody PointsTaskForm form) {
        taskService.create(form);
        return Result.success("新增成功", null);
    }

    @PutMapping
    @PreAuthorize("@ss.hasPerm('marketing:task:edit')")
    @OperLog(module = "积分任务", action = "UPDATE", description = "修改积分任务")
    public Result<Void> update(@Valid @RequestBody PointsTaskForm form) {
        taskService.update(form);
        return Result.success("修改成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('marketing:task:remove')")
    @OperLog(module = "积分任务", action = "DELETE", description = "删除积分任务")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success("删除成功", null);
    }
}
