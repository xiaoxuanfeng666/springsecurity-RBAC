package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.entity.SysOperationLog;
import com.example.rbac.mapper.SysOperationLogMapper;
import com.example.rbac.security.LoginUser;
import com.example.rbac.security.SecurityUtils;
import com.example.rbac.util.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志
 * <p>
 * 只做追加与查询，不提供修改删除。两类写入来源：
 * <ul>
 *   <li>业务代码直接记录（登录、登出、强制下线、停用/启用/离职、重置密码、自助改密）</li>
 *   <li>{@link com.example.rbac.aspect.OperLogAspect} 依据 {@code @OperLog} 注解自动记录增删改</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperationLogService {

    /** 前端传纯日期时补到当天最后一刻，避免把当天数据排除在外 */
    private static final int DATE_ONLY_LENGTH = 10;

    private static final int MAX_DESCRIPTION = 128;
    private static final int MAX_URI = 255;
    private static final int MAX_ERROR = 255;

    private final SysOperationLogMapper logMapper;

    /**
     * 业务代码直接记录：操作人取当前登录用户，无认证上下文（如登录动作本身）时视为本人操作
     */
    public void record(String module, String action, Long userId, String username, String description) {
        LoginUser operator = SecurityUtils.getLoginUser();
        if (operator == null) {
            save(module, action, description, userId, username, userId, username,
                    null, null, true, null, null);
            return;
        }
        save(module, action, description, userId, username,
                operator.getUser().getId(), operator.getUsername(),
                null, null, true, null, null);
    }

    /**
     * 供切面记录增删改：带请求信息与执行结果。
     * <p>
     * 这里的写入发生在业务事务提交**之后**，失败了也无法回滚业务，
     * 所以只记 error 日志而不向外抛 —— 否则会把已成功的操作回给前端一个失败。
     */
    public void recordAction(String module, String action, String description,
                             String httpMethod, String requestUri,
                             boolean success, String errorMsg, long durationMs) {
        LoginUser operator = SecurityUtils.getLoginUser();
        Long operatorId = operator == null ? null : operator.getUser().getId();
        String operatorName = operator == null ? null : operator.getUsername();
        try {
            save(module, action, description, null, null, operatorId, operatorName,
                    httpMethod, requestUri, success, errorMsg, durationMs);
        } catch (Exception e) {
            log.error("操作日志写入失败: module={}, action={}, uri={}", module, action, requestUri, e);
        }
    }

    private void save(String module, String action, String description,
                      Long userId, String username,
                      Long operatorId, String operatorName,
                      String httpMethod, String requestUri,
                      boolean success, String errorMsg, Long durationMs) {
        SysOperationLog entry = new SysOperationLog();
        entry.setModule(module);
        entry.setAction(action);
        entry.setDescription(truncate(description, MAX_DESCRIPTION));
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setOperatorId(operatorId);
        entry.setOperatorName(operatorName);
        entry.setHttpMethod(httpMethod);
        entry.setRequestUri(truncate(requestUri, MAX_URI));
        entry.setSuccess(success ? 1 : 0);
        entry.setErrorMsg(truncate(errorMsg, MAX_ERROR));
        entry.setDurationMs(durationMs);
        entry.setIp(WebUtils.getClientIp());
        logMapper.insert(entry);
        log.debug("操作日志已记录: module={}, action={}, success={}", module, action, success);
    }

    /** 异常信息等外部输入可能超长，截断后再写，避免插入失败 */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Page<SysOperationLog> page(long pageNum, long pageSize, String username, String module,
                                      String action, Integer success, String startTime, String endTime) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .like(StringUtils.hasText(username), SysOperationLog::getUsername, username)
                .eq(StringUtils.hasText(module), SysOperationLog::getModule, module)
                .eq(StringUtils.hasText(action), SysOperationLog::getAction, action)
                .eq(success != null, SysOperationLog::getSuccess, success)
                .ge(StringUtils.hasText(startTime), SysOperationLog::getCreateTime, startTime)
                .le(StringUtils.hasText(endTime), SysOperationLog::getCreateTime, endOfDay(endTime))
                .orderByDesc(SysOperationLog::getCreateTime)
                .orderByDesc(SysOperationLog::getId);
        return logMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private String endOfDay(String endTime) {
        if (endTime != null && endTime.length() == DATE_ONLY_LENGTH) {
            return endTime + " 23:59:59";
        }
        return endTime;
    }
}
