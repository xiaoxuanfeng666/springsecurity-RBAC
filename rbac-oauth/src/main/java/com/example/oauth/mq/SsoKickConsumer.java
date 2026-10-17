package com.example.oauth.mq;

import com.example.oauth.entity.SsoSessionRecord;
import com.example.oauth.security.OauthAuthStore;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.service.SsoSessionRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消费「强制下线」消息：真正让用户下线的地方
 * <p>
 * rbac-backend 只改了数据库里的会话状态，**令牌失效必须由授权服务来做** —— 因为
 * {@code /oauth/check_token} 是在这边判定的。这里按用户名删掉两样东西：
 * <ol>
 *   <li>登录态 {@code oauth2:auth:{token}}（先删，因为 {@code sso:browser} 要一起删掉）；</li>
 *   <li>SSO 会话 {@code sso:session:{username}} —— 删掉它，该用户在所有子系统手里的旧令牌
 *       下一个请求就失效，刷新令牌也换不出新的。</li>
 * </ol>
 * 删完再把库里那条记录兜底关掉（rbac-backend 已经改过，这里是为了「管理员改了库、
 * 消息却晚到」这种乱序也不留下「库里在线、实际已下线」的假象）。
 * <p>
 * 消息里只用 username 定位：SSO 会话本来就是按用户名存的，与浏览器无关 ——
 * 一个人在两个浏览器登录，管理员踢一次两边都失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rbac.sso.kick-topic:RBAC_OAUTH_SESSION}",
        consumerGroup = "${rbac.sso.kick-consumer-group:rbac-oauth-session-consumer}")
public class SsoKickConsumer implements RocketMQListener<String> {

    private final SsoSessionStore ssoSessionStore;
    private final OauthAuthStore oauthAuthStore;
    private final SsoSessionRecordService recordService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String body) {
        SsoKickMessage message = parse(body);
        if (message == null || !StringUtils.hasText(message.getUsername())) {
            log.warn("收到无法识别的强制下线消息，已忽略: {}", body);
            return;
        }
        String username = message.getUsername();

        String authToken = ssoSessionStore.getBrowserSessionId(username);
        if (authToken != null) {
            oauthAuthStore.delete(authToken);
        }
        ssoSessionStore.delete(username);
        recordService.closeOnlineRows(username, SsoSessionRecord.STATUS_KICKED, "被强制下线");

        log.info("已按强制下线通知清除会话: username={}, reason={}, sessionId={}",
                username, message.getReason(), message.getSessionId());
    }

    private SsoKickMessage parse(String body) {
        try {
            return objectMapper.readValue(body, SsoKickMessage.class);
        } catch (Exception e) {
            log.warn("强制下线消息解析失败: {}", e.getMessage());
            return null;
        }
    }
}
