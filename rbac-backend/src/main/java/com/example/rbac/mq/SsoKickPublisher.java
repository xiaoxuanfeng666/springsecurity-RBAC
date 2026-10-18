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
 * 通知 rbac-oauth 强制下线某个用户的 SSO 会话
 * <p>
 * 管理员的几类动作（停用、离职、删除、重置密码、强制下线）都要求「立即失效该用户在所有子系统的登录态」。
 * 令牌由 rbac-oauth 签发，删除 SSO 会话的代码也在那边，所以这里只负责发 MQ；
 * 消费方 {@code SsoKickConsumer} 按用户名删掉
 * {@code sso:session:{username}} 与 {@code oauth2:auth:{token}}，并关闭库里的在线会话记录。
 * <p>
 * 发送时机：
 * <ul>
 *   <li>有事务时注册 {@code afterCommit}，避免「消息发了、事务回滚了」——那会让用户被莫名踢下线；</li>
 *   <li>无事务时立即发送。</li>
 * </ul>
 * 发送失败只记日志，不影响管理员的业务操作：本地会话已经被删，SSO 会话最迟在自然过期前仍可用，
 * 但下一次同类操作或人工强制下线可以补上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoKickPublisher {

    /** 目前只有这些动作会触发 SSO 下线，取值与 rbac-oauth 的 SsoKickMessage 保持一致 */
    public static final String TYPE_KICK = SsoKickMessage.TYPE_KICK;
    public static final String TYPE_DISABLED = SsoKickMessage.TYPE_DISABLED;
    public static final String TYPE_RESIGNED = SsoKickMessage.TYPE_RESIGNED;
    public static final String TYPE_DELETE = SsoKickMessage.TYPE_DELETE;
    public static final String TYPE_RESET_PWD = SsoKickMessage.TYPE_RESET_PWD;

    /**
     * 用 ObjectProvider 而不是直接注入：未配 rocketmq.name-server 时 starter 不会创建该 Bean，
     * 直接注入会导致整个应用起不来。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    private final ObjectMapper objectMapper;

    @Value("${rbac.sso.kick-topic:RBAC_OAUTH_SESSION}")
    private String kickTopic;

    /**
     * @param type      动作类型，见上面的 TYPE_* 常量
     * @param sessionId 被下线的会话记录 id，没有具体记录（按用户整体踢下线）时传 null
     * @param userId    用户 id
     * @param username  登录名 —— 消费方按它定位 SSO 会话
     * @param reason    展示在会话记录里的下线原因
     */
    public void publish(String type, Long sessionId, Long userId, String username, String reason) {
        if (!StringUtils.hasText(username)) {
            log.warn("缺少用户名，跳过 SSO 强制下线通知: type={}, userId={}", type, userId);
            return;
        }
        SsoKickMessage message = new SsoKickMessage();
        message.setType(type);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setUsername(username);
        message.setReason(reason);
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

    private void send(SsoKickMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置（rocketmq.name-server 为空），跳过 SSO 强制下线通知: username={}",
                    message.getUsername());
            return;
        }
        try {
            template.syncSend(kickTopic, objectMapper.writeValueAsString(message));
            log.info("已发送 SSO 强制下线通知: username={}, type={}, reason={}",
                    message.getUsername(), message.getType(), message.getReason());
        } catch (Exception e) {
            log.error("发送 SSO 强制下线通知失败: topic={}, username={}, type={}",
                    kickTopic, message.getUsername(), message.getType(), e);
        }
    }
}
