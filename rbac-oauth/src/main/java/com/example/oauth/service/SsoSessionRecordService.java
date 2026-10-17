package com.example.oauth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.oauth.entity.SsoSessionRecord;
import com.example.oauth.mapper.SsoSessionRecordMapper;
import com.example.oauth.security.LoginUser;
import com.example.oauth.security.SsoSessionStore;
import com.example.oauth.util.DeviceInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SSO 登录会话的落库记录（表 {@code sys_sso_session}）
 * <p>
 * Redis 里的 {@code sso:session:{username}} 决定「令牌能不能用」，但它是会过期的开关，
 * 没有历史也没有设备信息。这张表负责回答三个问题：谁在什么时候、从哪台设备登录；
 * 现在还在不在线；是被谁、以什么方式下线的。
 * <p>
 * 写入方只有本服务（rbac-oauth）：它才是那个「看着用户浏览器登录」的一方，能拿到 IP 与 User-Agent。
 * rbac-backend 只读这张表，并在「强制下线」时把状态改成 2、同时发 MQ 通知本服务删 Redis 会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoSessionRecordService {

    private final SsoSessionRecordMapper recordMapper;
    private final SsoSessionStore ssoSessionStore;

    /**
     * 表单登录成功时落一行
     * <p>
     * 单会话模型：同一用户之前仍在线的记录会被置为「被新登录顶替」，
     * 保证列表里一个人最多只有一条「在线」——这与 {@code OauthAuthStore} 只保留一个登录态一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordLogin(LoginUser loginUser, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        closeOnlineRows(loginUser.getUsername(), SsoSessionRecord.STATUS_OFFLINE, "被新登录顶替");

        SsoSessionRecord record = new SsoSessionRecord();
        record.setUserId(loginUser.getUser().getId());
        record.setUsername(loginUser.getUsername());
        record.setNickname(loginUser.getUser().getNickname());
        record.setLoginIp(ip);
        record.setUserAgent(userAgent);
        record.setDevice(DeviceInfoUtils.describe(userAgent));
        record.setLoginTime(now);
        record.setStatus(SsoSessionRecord.STATUS_ONLINE);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        recordMapper.insert(record);
        log.info("SSO 登录已记录: username={}, device={}, ip={}",
                record.getUsername(), record.getDevice(), ip);
    }

    /**
     * 关闭某用户仍在线的记录（主动登出 / 被强制下线）
     *
     * @return 受影响行数；该用户本来就没有在线记录时返回 0（登出是幂等的，不算错误）
     */
    public int closeOnlineRows(String username, int status, String reason) {
        if (!StringUtils.hasText(username)) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        SsoSessionRecord update = new SsoSessionRecord();
        update.setStatus(status);
        update.setLogoutTime(now);
        update.setLogoutReason(reason);
        update.setUpdateTime(now);
        return recordMapper.update(update, new LambdaUpdateWrapper<SsoSessionRecord>()
                .eq(SsoSessionRecord::getUsername, username)
                .eq(SsoSessionRecord::getStatus, SsoSessionRecord.STATUS_ONLINE));
    }

    /**
     * 定时对齐库里的「在线」与 Redis 里的真实会话
     * <p>
     * 用户直接关掉浏览器、或者会话到 7 天自然过期时，没有任何请求会来触发登出，
     * 库里那条「在线」就会一直挂着。这里定期核对：Redis 里已经没有这个会话的，就把记录标成「会话过期」。
     * 一次只扫 status=1 的行（同一时刻一个人最多一条），开销可以忽略。
     */
    @Scheduled(fixedDelayString = "${rbac.sso.reconcile-interval-ms:60000}")
    public void reconcile() {
        List<SsoSessionRecord> online = recordMapper.selectList(
                new LambdaQueryWrapper<SsoSessionRecord>()
                        .eq(SsoSessionRecord::getStatus, SsoSessionRecord.STATUS_ONLINE));
        for (SsoSessionRecord record : online) {
            if (ssoSessionStore.exists(record.getUsername())) {
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            SsoSessionRecord update = new SsoSessionRecord();
            update.setId(record.getId());
            update.setStatus(SsoSessionRecord.STATUS_OFFLINE);
            update.setLogoutTime(now);
            update.setLogoutReason("会话过期");
            update.setUpdateTime(now);
            recordMapper.updateById(update);
            log.info("SSO 会话已过期，回填记录状态: username={}", record.getUsername());
        }
    }
}
