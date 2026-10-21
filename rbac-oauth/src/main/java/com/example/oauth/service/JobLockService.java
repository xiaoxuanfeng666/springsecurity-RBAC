package com.example.oauth.service;

import com.example.oauth.mapper.JobLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.UUID;

/**
 * 定时任务多实例互斥（基于 {@code sys_job_lock} 的租约行锁）
 * <p>
 * 定时任务在每个实例上都会触发，这里用一行锁记录选举“本轮由谁执行”：
 * 抢到才跑，没抢到直接跳过。锁是租约式的 —— 不显式释放，
 * 到期时间取“略小于执行周期”，实例崩溃后最多一个周期就能被其他实例接管。
 * <p>
 * 为什么不直接用 SELECT ... FOR UPDATE 一直持有：这个任务中间要做 Redis 查询和多次更新，
 * 事务会把行锁持有到整个任务结束；租约方案只在一条 UPDATE 期间持有行锁，任务本身无需包事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLockService {

    private final JobLockMapper jobLockMapper;

    /** 当前实例标识，便于在锁行上排查是哪个节点在跑 */
    private final String holder = holder();

    /**
     * 尝试获取任务锁。
     *
     * @param jobName      任务名，对应 sys_job_lock.job_name
     * @param leaseSeconds 租约秒数，应略小于调度周期
     * @return true 表示本轮由当前实例执行
     */
    public boolean tryAcquire(String jobName, int leaseSeconds) {
        try {
            return jobLockMapper.tryAcquire(jobName, holder, leaseSeconds) == 1;
        } catch (Exception e) {
            // 锁表不可用时不阻塞业务，宁可跳过本轮（任务本身幂等，下一轮会补上）
            log.warn("获取定时任务锁失败，本轮跳过: job={}, error={}", jobName, e.getMessage());
            return false;
        }
    }

    private static String holder() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        return host + ":" + UUID.randomUUID().toString().substring(0, 8);
    }
}
