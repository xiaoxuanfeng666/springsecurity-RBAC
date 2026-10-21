package com.example.oauth.mq;

import com.example.oauth.oauth.ClientDetailsCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费「客户端配置变更」消息：删掉 rbac-oauth 的客户端缓存
 * <p>
 * rbac-backend 在管理端编辑 / 删除客户端后发出，这里只做一件事：删
 * {@code oauth2:client:{clientId}}。下一次令牌请求读不到缓存，会回源数据库重建。
 * 删除操作天然幂等，消息重投也安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rbac.client.change-topic:RBAC_CLIENT_CHANGE}",
        consumerGroup = "${rbac.client.change-consumer-group:rbac-oauth-client-consumer}")
public class ClientChangeConsumer implements RocketMQListener<String> {

    private final ClientDetailsCacheStore cacheStore;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        ClientChangeMessage message = parse(body);
        if (message == null || !StringUtils.hasText(message.getClientId())) {
            log.warn("收到无法识别的客户端变更消息，已忽略: {}", body);
            return;
        }
        cacheStore.evict(message.getClientId());
        log.info("已清除客户端缓存: clientId={}, type={}", message.getClientId(), message.getType());
    }

    private ClientChangeMessage parse(String body) {
        try {
            return objectMapper.readValue(body, ClientChangeMessage.class);
        } catch (Exception e) {
            log.warn("客户端变更消息解析失败: {}", e.getMessage());
            return null;
        }
    }
}
