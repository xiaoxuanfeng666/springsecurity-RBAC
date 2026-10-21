package com.example.rbac.mq;

import com.example.rbac.security.TokenRevocationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费 rbac-oauth 广播的「会话已撤销」
 * <p>
 * 用默认的集群模式：撤销状态写在共享 Redis 里，同组内一个实例消费一次即可让本服务所有实例看到。
 * 解析失败丢弃（重投结果相同），记录撤销是幂等操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rbac.session.revoke-topic:RBAC_SESSION_REVOKE}",
        consumerGroup = "rbac-backend-session-revoke-consumer")
public class SessionRevokeConsumer implements RocketMQListener<String> {

    private final TokenRevocationStore tokenRevocationStore;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String payload) {
        SessionRevokeMessage message;
        try {
            message = objectMapper.readValue(payload, SessionRevokeMessage.class);
        } catch (Exception e) {
            log.warn("会话撤销消息解析失败，已丢弃: {}", payload, e);
            return;
        }
        if (message == null || !StringUtils.hasText(message.getSid())) {
            log.warn("会话撤销消息缺少 sid，已丢弃: {}", payload);
            return;
        }
        tokenRevocationStore.mark(message.getSid(), message.getTtlSeconds());
        log.info("已消费会话撤销: sid={}, username={}", message.getSid(), message.getUsername());
    }
}
