package com.example.order.mq;

import com.example.order.security.LoginUserCacheStore;
import com.example.order.service.OrderSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费 RBAC 下发的子系统数据
 * <p>
 * topic 与 rbac-backend 的生产者一致，tag 是本系统的子系统编码 ——
 * RBAC 用 tag 区分不同子系统，这里只订阅自己的那条。
 * <p>
 * 解析失败直接吞掉并记日志：这类"毒消息"重投多少次都一样，重试只会刷屏；
 * 落库失败则抛出，交给容器重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "RBAC_SUBSYSTEM_SYNC",
        selectorExpression = OrderSyncService.SUBSYSTEM_CODE,
        consumerGroup = "order-sync-consumer")
public class SubsystemSyncConsumer implements RocketMQListener<String> {

    private final OrderSyncService syncService;
    private final LoginUserCacheStore loginUserCacheStore;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String payload) {
        SubsystemSyncMessage message;
        try {
            message = objectMapper.readValue(payload, SubsystemSyncMessage.class);
        } catch (Exception e) {
            log.error("解析子系统同步消息失败，丢弃该消息: {}", payload, e);
            return;
        }
        if (!OrderSyncService.SUBSYSTEM_CODE.equals(message.getSubsystemCode())) {
            // 理论上 tag 过滤已经挡住了，这里再兜一层，防止 tag 配错时把别人的数据灌进来
            log.warn("消息的子系统编码与本系统不符，丢弃: {}", message.getSubsystemCode());
            return;
        }
        syncService.rebuild(message);
        // 重建已提交：用户/角色/菜单任一变化都会让缓存里的权限点失真，整体清空
        loginUserCacheStore.evictAll();
    }
}
