package com.example.rbac.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 通知 rbac-oauth 失效客户端配置缓存
 * <p>
 * 管理端编辑 / 删除客户端后，rbac-oauth 里的 {@code oauth2:client:{clientId}} 必须立刻作废，
 * 否则旧配置要等 TTL 到期才失效。缓存代码在 rbac-oauth 那边，所以这里只负责发 MQ；
 * 消费方 {@code ClientChangeConsumer} 删掉对应的 Redis 键。
 * <p>
 * 发送时机与 {@link SsoKickPublisher} 一致：有事务时注册 {@code afterCommit}，
 * 避免「消息发了、事务回滚了」；无事务时立即发送。发送失败只记日志 ——
 * 缓存最迟在 TTL 到期后自然恢复正确，不影响管理端操作本身。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientChangePublisher {

    /**
     * 用 ObjectProvider 而不是直接注入：未配 rocketmq.name-server 时 starter 不会创建该 Bean，
     * 直接注入会导致整个应用起不来。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    private final ObjectMapper objectMapper;

    @Value("${rbac.client.change-topic:RBAC_CLIENT_CHANGE}")
    private String changeTopic;

    /**
     * @param type     变更类型，取值见 {@link ClientChangeMessage#TYPE_UPDATE} 等
     * @param clientId 变更的客户端标识，消费方据此定位缓存键
     */
    public void publish(String type, String clientId) {
        if (!StringUtils.hasText(clientId)) {
            log.warn("缺少客户端标识，跳过缓存失效通知: type={}", type);
            return;
        }
        ClientChangeMessage message = new ClientChangeMessage();
        message.setType(type);
        message.setClientId(clientId);
        message.setTimestamp(System.currentTimeMillis());

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(message);
            }
        });
    }

    private void send(ClientChangeMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置（rocketmq.name-server 为空），跳过客户端缓存失效通知: clientId={}",
                    message.getClientId());
            return;
        }
        try {
            template.syncSend(changeTopic, objectMapper.writeValueAsString(message));
            log.info("已发送客户端缓存失效通知: clientId={}, type={}",
                    message.getClientId(), message.getType());
        } catch (Exception e) {
            log.error("发送客户端缓存失效通知失败: topic={}, clientId={}, type={}",
                    changeTopic, message.getClientId(), message.getType(), e);
        }
    }
}
