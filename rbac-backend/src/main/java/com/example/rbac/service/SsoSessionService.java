package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.entity.SsoSessionRecord;
import com.example.rbac.mapper.SsoSessionRecordMapper;
import com.example.rbac.mq.SsoKickPublisher;
import com.example.rbac.security.LoginUser;
import com.example.rbac.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SsoKickPublisher ssoKickPublisher;

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

        ssoKickPublisher.publish(SsoKickPublisher.TYPE_KICK, record.getId(),
                record.getUserId(), record.getUsername(), REASON_KICKED);
        logService.record(MODULE_SSO, AuditAction.KICK, record.getUserId(),
                record.getUsername(), "强制下线 SSO 会话");
        log.info("已强制下线 SSO 会话: username={}, sessionId={}", record.getUsername(), sessionId);
    }
}
