package com.example.audit.mq;

import com.example.audit.security.LoginUserCacheStore;
import com.example.audit.security.TokenRevocationStore;
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
 * 用默认的集群模式：撤销状态写在共享 Redis 里，同组内一个实例消费一次即可让本系统所有实例看到。
 * 解析失败丢弃（重投结果相同），记录撤销是幂等操作。
 * <p>
 * 除了记 sid，还要清掉该用户的权限缓存：停用/离职/删除未必都会触发子系统全量同步，
 * 但一定会走这条撤权消息，这里清掉才不会让缓存里的旧身份/权限继续被使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rbac.session.revoke-topic:RBAC_SESSION_REVOKE}",
        consumerGroup = "audit-session-revoke-consumer")
public class SessionRevokeConsumer implements RocketMQListener<String> {

    private final TokenRevocationStore tokenRevocationStore;
    private final LoginUserCacheStore loginUserCacheStore;
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
        loginUserCacheStore.evict(message.getUsername());
        log.info("已消费会话撤销: sid={}, username={}", message.getSid(), message.getUsername());
    }
}
