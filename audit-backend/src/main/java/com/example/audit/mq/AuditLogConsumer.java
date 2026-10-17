package com.example.audit.mq;

import com.example.audit.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费各系统上报的增删改审计日志
 * <p>
 * 所有来源共用一个 topic（RBAC_AUDIT_LOG），来源系统由消息体的 source 字段区分。
 * <p>
 * 解析失败直接吞掉并记日志：这类"毒消息"重投多少次都一样，重试只会刷屏；
 * 落库失败则抛出，交给容器重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "RBAC_AUDIT_LOG",
        consumerGroup = "audit-log-consumer")
public class AuditLogConsumer implements RocketMQListener<String> {

    private final AuditLogService logService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String payload) {
        AuditLogMessage message;
        try {
            message = objectMapper.readValue(payload, AuditLogMessage.class);
        } catch (Exception e) {
            log.error("解析审计日志消息失败，丢弃该消息: {}", payload, e);
            return;
        }
        if (!StringUtils.hasText(message.getSource()) || !StringUtils.hasText(message.getAction())) {
            log.warn("审计日志消息缺少 source/action，丢弃: {}", payload);
            return;
        }
        logService.insert(message);
    }
}
