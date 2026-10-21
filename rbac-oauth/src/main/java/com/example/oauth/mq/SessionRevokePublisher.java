package com.example.oauth.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 广播「会话已撤销」给所有子系统
 * <p>
 * 子系统本地验签后看不到会话是否存活，只能靠这条消息得知「这个 sid 作废了」。
 * 所有会话销毁路径（统一登出、后台踢人、绝对超时、新登录轮换旧会话）最终都汇到
 * {@code SsoSessionStore.delete} 或轮换分支，由那里调用本类发布。
 * <p>
 * 发送失败只记日志、不重试：撤销集合只是「提前生效」的优化，令牌自身的 {@code exp}
 * （30 分钟）是最坏情况下的兜底窗口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevokePublisher {

    /** 用 ObjectProvider：未配 rocketmq.name-server 时 starter 不创建该 Bean，直接注入会导致启动失败 */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    private final ObjectMapper objectMapper;

    @Value("${rbac.session.revoke-topic:RBAC_SESSION_REVOKE}")
    private String revokeTopic;

    public void publish(String sid, String username, long ttlSeconds) {
        if (!StringUtils.hasText(sid)) {
            return;
        }
        SessionRevokeMessage message = new SessionRevokeMessage();
        message.setSid(sid);
        message.setUsername(username);
        message.setTtlSeconds(ttlSeconds);
        message.setTimestamp(System.currentTimeMillis());

        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置，跳过会话撤销广播: sid={}, username={}", sid, username);
            return;
        }
        try {
            template.syncSend(revokeTopic, objectMapper.writeValueAsString(message));
            log.info("已广播会话撤销: sid={}, username={}", sid, username);
        } catch (Exception e) {
            log.error("广播会话撤销失败: topic={}, sid={}, username={}", revokeTopic, sid, username, e);
        }
    }
}
