package com.example.oauth.task;

import com.example.oauth.service.JobLockService;
import com.example.oauth.service.SsoSessionRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSO 会话对账的定时任务
 * <p>
 * 只负责「何时跑、多实例谁跑」，具体核对逻辑在 {@link SsoSessionRecordService#reconcileExpiredSessions()}。
 * 定时任务集中放在 {@code task} 包，业务服务不感知调度。
 * <p>
 * 多实例部署时每个实例都会触发，抢到 {@code sys_job_lock} 租约的那个才执行；
 * 租约略小于调度周期，实例崩溃后最多一轮即可被接管。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoSessionReconcileTask {

    /** 对账任务在锁表中的任务名 */
    private static final String JOB_RECONCILE = "sso-session-reconcile";

    /** 锁租约（秒）：略小于 60 秒的调度周期 */
    private static final int RECONCILE_LOCK_SECONDS = 55;

    private final JobLockService jobLockService;
    private final SsoSessionRecordService ssoSessionRecordService;

    @Scheduled(fixedDelayString = "${rbac.sso.reconcile-interval-ms:60000}")
    public void reconcile() {
        if (!jobLockService.tryAcquire(JOB_RECONCILE, RECONCILE_LOCK_SECONDS)) {
            log.debug("另一实例正在执行 SSO 会话对账，本轮跳过");
            return;
        }
        ssoSessionRecordService.reconcileExpiredSessions();
    }
}
