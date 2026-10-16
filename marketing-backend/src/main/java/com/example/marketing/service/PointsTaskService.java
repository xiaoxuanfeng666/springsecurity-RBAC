package com.example.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import com.example.marketing.dto.PointsTaskForm;
import com.example.marketing.entity.PointsTask;
import com.example.marketing.mapper.PointsTaskMapper;
import com.example.marketing.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 积分任务配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsTaskService {

    /** task_code 列长度 */
    private static final int TASK_CODE_MAX_LENGTH = 64;

    private final PointsTaskMapper taskMapper;

    public Page<PointsTask> page(long pageNum, long pageSize, String taskName, Integer status) {
        LambdaQueryWrapper<PointsTask> wrapper = new LambdaQueryWrapper<PointsTask>()
                .like(StringUtils.hasText(taskName), PointsTask::getTaskName, taskName)
                .eq(status != null, PointsTask::getStatus, status)
                .orderByAsc(PointsTask::getId);
        return taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public PointsTask getById(Long id) {
        PointsTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_EXIST);
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(PointsTaskForm form) {
        if (codeExists(form.getTaskCode(), null)) {
            throw new BusinessException(ResultCode.TASK_CODE_EXISTS);
        }
        PointsTask task = new PointsTask();
        copy(form, task);
        taskMapper.insert(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(PointsTaskForm form) {
        PointsTask exist = getById(form.getId());
        if (!exist.getTaskCode().equals(form.getTaskCode())
                && codeExists(form.getTaskCode(), form.getId())) {
            throw new BusinessException(ResultCode.TASK_CODE_EXISTS);
        }
        PointsTask task = new PointsTask();
        task.setId(form.getId());
        copy(form, task);
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PointsTask task = getById(id);
        releaseCode(task);
        taskMapper.deleteById(id);
    }

    private boolean codeExists(String taskCode, Long excludeId) {
        LambdaQueryWrapper<PointsTask> wrapper = new LambdaQueryWrapper<PointsTask>()
                .eq(PointsTask::getTaskCode, taskCode)
                .ne(excludeId != null, PointsTask::getId, excludeId);
        Long count = taskMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 腾出 task_code 唯一键：删除是逻辑删除，行仍在表里、唯一键仍被占用，
     * 不改名的话这个任务编码就永远无法再使用
     */
    private void releaseCode(PointsTask task) {
        PointsTask renamed = new PointsTask();
        renamed.setId(task.getId());
        renamed.setTaskCode(UniqueKeyUtils.release(
                task.getTaskCode(), task.getId(), TASK_CODE_MAX_LENGTH));
        taskMapper.updateById(renamed);
    }

    private void copy(PointsTaskForm form, PointsTask task) {
        task.setTaskName(form.getTaskName());
        task.setTaskCode(form.getTaskCode());
        task.setTaskType(StringUtils.hasText(form.getTaskType()) ? form.getTaskType() : "daily");
        task.setPoints(form.getPoints() == null ? 0 : form.getPoints());
        task.setDailyLimit(form.getDailyLimit() == null ? 1 : form.getDailyLimit());
        task.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        task.setRemark(form.getRemark());
    }
}
