package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.entity.SysLoginAudit;
import com.example.rbac.mapper.SysLoginAuditMapper;
import com.example.rbac.security.LoginUser;
import com.example.rbac.security.SecurityUtils;
import com.example.rbac.util.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录/下线审计
 * <p>
 * 只做追加与查询，不提供修改删除。记录点覆盖：登录、登出、强制下线、
 * 停用下线、离职下线、重置密码下线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginAuditService {

    /** 前端传纯日期时补到当天最后一刻，避免把当天数据排除在外 */
    private static final int DATE_ONLY_LENGTH = 10;

    private final SysLoginAuditMapper auditMapper;

    /**
     * 记录审计：操作人取当前登录用户；无认证上下文（如登录动作本身）时视为本人操作
     */
    public void record(String action, Long userId, String username, String reason) {
        LoginUser operator = SecurityUtils.getLoginUser();
        if (operator == null) {
            record(action, userId, username, userId, username, reason);
            return;
        }
        record(action, userId, username,
                operator.getUser().getId(), operator.getUsername(), reason);
    }

    public void record(String action, Long userId, String username,
                       Long operatorId, String operatorName, String reason) {
        SysLoginAudit audit = new SysLoginAudit();
        audit.setAction(action);
        audit.setUserId(userId);
        audit.setUsername(username);
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setReason(reason);
        audit.setIp(WebUtils.getClientIp());
        auditMapper.insert(audit);
        log.debug("审计已记录: action={}, target={}, operator={}", action, username, operatorName);
    }

    public Page<SysLoginAudit> page(long pageNum, long pageSize, String username,
                                    String action, String startTime, String endTime) {
        LambdaQueryWrapper<SysLoginAudit> wrapper = new LambdaQueryWrapper<SysLoginAudit>()
                .like(StringUtils.hasText(username), SysLoginAudit::getUsername, username)
                .eq(StringUtils.hasText(action), SysLoginAudit::getAction, action)
                .ge(StringUtils.hasText(startTime), SysLoginAudit::getCreateTime, startTime)
                .le(StringUtils.hasText(endTime), SysLoginAudit::getCreateTime, endOfDay(endTime))
                .orderByDesc(SysLoginAudit::getCreateTime)
                .orderByDesc(SysLoginAudit::getId);
        return auditMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    private String endOfDay(String endTime) {
        if (endTime != null && endTime.length() == DATE_ONLY_LENGTH) {
            return endTime + " 23:59:59";
        }
        return endTime;
    }
}
