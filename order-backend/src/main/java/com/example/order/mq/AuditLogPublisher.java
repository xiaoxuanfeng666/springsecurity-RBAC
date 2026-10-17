package com.example.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把增删改操作上报给审计日志系统
 * <p>
 * topic 只有一条 {@code RBAC_AUDIT_LOG}，来源系统由消息体的 source 字段区分。
 * <p>
 * 用 {@link ObjectProvider} 取模板：未配 rocketmq.name-server 时 starter 不创建该 Bean，
 * 直接注入会让应用起不来 —— 审计上报是附加能力，不该拖垮业务。
 * 发送失败只记日志：业务已经成功，不能因为审计系统暂时不可用把结果改成失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogPublisher {

    /** 审计日志 topic */
    public static final String TOPIC = "RBAC_AUDIT_LOG";

    /** 本服务的来源标识 */
    public static final String SOURCE = "order-center";

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    private final ObjectMapper objectMapper;

    public void publish(AuditLogMessage message) {
        if (message.getSource() == null) {
            message.setSource(SOURCE);
        }
        // 有事务时等提交后再发，避免业务回滚了而审计日志已经上报
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doPublish(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(message);
            }
        });
    }

    private void doPublish(AuditLogMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置，跳过审计日志上报: source={}, uri={}",
                    message.getSource(), message.getRequestUri());
            return;
        }
        try {
            template.syncSend(TOPIC, objectMapper.writeValueAsString(message));
            log.debug("审计日志已上报: source={}, module={}, action={}",
                    message.getSource(), message.getModule(), message.getAction());
        } catch (Exception e) {
            log.error("审计日志上报失败: source={}, module={}, action={}",
                    message.getSource(), message.getModule(), message.getAction(), e);
        }
    }
}
