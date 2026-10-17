package com.example.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.audit.common.BusinessException;
import com.example.audit.common.ResultCode;
import com.example.audit.entity.AuditLogRecord;
import com.example.audit.mapper.AuditLogMapper;
import com.example.audit.mq.AuditLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 审计日志
 * <p>
 * 只做接收与查询：日志来自各系统经 MQ 上报的消息，本系统不产生业务数据。
 * 只记录增删改（CREATE/UPDATE/DELETE/GRANT），不记录登录/登出。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 前端传纯日期时补到当天最后一刻，避免把当天数据排除在外 */
    private static final int DATE_ONLY_LENGTH = 10;

    private static final int MAX_SOURCE = 32;
    private static final int MAX_MODULE = 64;
    private static final int MAX_ACTION = 16;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_OPERATOR = 64;
    private static final int MAX_HTTP_METHOD = 16;
    private static final int MAX_URI = 255;
    private static final int MAX_PARAMS = 8000;
    private static final int MAX_ERROR = 1000;
    private static final int MAX_IP = 64;

    private final AuditLogMapper logMapper;

    /**
     * 消费 MQ 消息并落库
     * <p>
     * 调用方（{@link com.example.audit.mq.AuditLogConsumer}）负责把异常抛出去让容器重试，
     * 这里不做 try-catch。
     */
    public void insert(AuditLogMessage message) {
        AuditLogRecord entry = new AuditLogRecord();
        entry.setSource(truncate(message.getSource(), MAX_SOURCE));
        entry.setModule(truncate(message.getModule(), MAX_MODULE));
        entry.setAction(truncate(message.getAction(), MAX_ACTION));
        entry.setDescription(truncate(message.getDescription(), MAX_DESCRIPTION));
        entry.setOperatorId(message.getOperatorId());
        entry.setOperatorName(truncate(message.getOperatorName(), MAX_OPERATOR));
        entry.setHttpMethod(truncate(message.getHttpMethod(), MAX_HTTP_METHOD));
        entry.setRequestUri(truncate(message.getRequestUri(), MAX_URI));
        entry.setRequestParams(truncate(message.getRequestParams(), MAX_PARAMS));
        entry.setSuccess(message.getSuccess() == null ? 1 : message.getSuccess());
        entry.setErrorMsg(truncate(message.getErrorMsg(), MAX_ERROR));
        entry.setDurationMs(message.getDurationMs());
        entry.setIp(truncate(message.getIp(), MAX_IP));
        entry.setCreateTime(toLocalDateTime(message.getTimestamp()));
        logMapper.insert(entry);
        log.debug("审计日志已落库: source={}, module={}, action={}, success={}",
                entry.getSource(), entry.getModule(), entry.getAction(), entry.getSuccess());
    }

    public Page<AuditLogRecord> page(long pageNum, long pageSize, String source, String module,
                                     String action, String operatorName, Integer success,
                                     String startTime, String endTime) {
        LambdaQueryWrapper<AuditLogRecord> wrapper = new LambdaQueryWrapper<AuditLogRecord>()
                .eq(StringUtils.hasText(source), AuditLogRecord::getSource, source)
                .eq(StringUtils.hasText(module), AuditLogRecord::getModule, module)
                .eq(StringUtils.hasText(action), AuditLogRecord::getAction, action)
                .like(StringUtils.hasText(operatorName), AuditLogRecord::getOperatorName, operatorName)
                .eq(success != null, AuditLogRecord::getSuccess, success)
                .ge(StringUtils.hasText(startTime), AuditLogRecord::getCreateTime, startTime)
                .le(StringUtils.hasText(endTime), AuditLogRecord::getCreateTime, endOfDay(endTime))
                .orderByDesc(AuditLogRecord::getCreateTime)
                .orderByDesc(AuditLogRecord::getId);
        return logMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public AuditLogRecord detail(Long id) {
        AuditLogRecord record = logMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.AUDIT_NOT_EXIST);
        }
        return record;
    }

    private LocalDateTime toLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    private String endOfDay(String endTime) {
        if (endTime != null && endTime.length() == DATE_ONLY_LENGTH) {
            return endTime + " 23:59:59";
        }
        return endTime;
    }

    /** 外部输入可能超长，截断后再写，避免插入失败 */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
