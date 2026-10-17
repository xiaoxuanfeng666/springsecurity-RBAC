package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.entity.SsoSessionRecord;
import com.example.rbac.mapper.SsoSessionRecordMapper;
import com.example.rbac.mq.SsoKickMessage;
import com.example.rbac.security.LoginUser;
import com.example.rbac.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * SSO 登录会话（表 {@code sys_sso_session}）
 * <p>
 * 记录由 rbac-oauth 写入 —— 只有它看得到用户的浏览器（IP / User-Agent）。
 * 本服务只读这张表做「SSO 会话」列表；强制下线时把状态改成 2，并发 MQ 通知 rbac-oauth
 * 删掉 Redis 里的会话（令牌真正失效的地方在授权服务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoSessionService {

    /** 操作日志模块名 */
    private static final String MODULE_SSO = "SSO会话";

    private static final String REASON_KICKED = "被强制下线";

    private final SsoSessionRecordMapper recordMapper;
    private final SysOperationLogService logService;
    private final ObjectMapper objectMapper;

    /**
     * 用 ObjectProvider 而不是直接注入：未配 rocketmq.name-server 时 starter 不会创建该 Bean，
     * 直接注入会导致整个应用起不来。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    @Value("${rbac.sso.kick-topic:RBAC_OAUTH_SESSION}")
    private String kickTopic;

    public Page<SsoSessionRecord> page(long pageNum, long pageSize, String username, Integer status) {
        LambdaQueryWrapper<SsoSessionRecord> wrapper = new LambdaQueryWrapper<SsoSessionRecord>()
                .like(StringUtils.hasText(username), SsoSessionRecord::getUsername, username)
                .eq(status != null, SsoSessionRecord::getStatus, status)
                .orderByDesc(SsoSessionRecord::getLoginTime)
                .orderByDesc(SsoSessionRecord::getId);
        return recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 强制下线某条 SSO 会话
     * <p>
     * 本地只负责把记录改成「被强制下线」；令牌失效必须由 rbac-oauth 消费 MQ 后删 Redis 会话完成。
     * 消费者那边也会兜底关一次行，所以「管理员改了库、消息却晚到」这种乱序不会留下在线假象。
     */
    public void kick(Long sessionId) {
        SsoSessionRecord record = recordMapper.selectById(sessionId);
        if (record == null) {
            throw new BusinessException(ResultCode.SSO_SESSION_NOT_EXIST);
        }
        LoginUser operator = SecurityUtils.getLoginUser();
        if (operator != null && operator.getUser().getId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.KICK_SELF);
        }

        if (Integer.valueOf(SsoSessionRecord.STATUS_ONLINE).equals(record.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            SsoSessionRecord update = new SsoSessionRecord();
            update.setId(record.getId());
            update.setStatus(SsoSessionRecord.STATUS_KICKED);
            update.setLogoutTime(now);
            update.setLogoutReason(REASON_KICKED);
            update.setUpdateTime(now);
            recordMapper.updateById(update);
        }

        sendKick(record);
        logService.record(MODULE_SSO, AuditAction.KICK, record.getUserId(),
                record.getUsername(), "强制下线 SSO 会话");
        log.info("已强制下线 SSO 会话: username={}, sessionId={}", record.getUsername(), sessionId);
    }

    private void sendKick(SsoSessionRecord record) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置（rocketmq.name-server 为空），跳过强制下线通知: username={}",
                    record.getUsername());
            return;
        }
        SsoKickMessage message = new SsoKickMessage();
        message.setType(SsoKickMessage.TYPE_KICK);
        message.setSessionId(record.getId());
        message.setUserId(record.getUserId());
        message.setUsername(record.getUsername());
        message.setReason(REASON_KICKED);
        message.setTimestamp(System.currentTimeMillis());
        try {
            template.syncSend(kickTopic, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            // 发消息失败不回滚本地状态：消费者会兜底关行，但 Redis 会话这次没删掉，下次强制下线或重启可补
            log.error("发送强制下线消息失败: topic={}, username={}", kickTopic, record.getUsername(), e);
        }
    }
}
