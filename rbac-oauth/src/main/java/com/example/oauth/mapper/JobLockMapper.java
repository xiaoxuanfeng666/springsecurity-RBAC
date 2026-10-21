package com.example.oauth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 定时任务分布式锁（表 {@code sys_job_lock}，一个任务一行）
 * <p>
 * 用一条条件 UPDATE 抢锁：只有“未加锁或租约已过期”时才能真正更新到行，
 * 受影响行数为 1 才表示本实例拿到了本轮执行权。行锁只在 UPDATE 语句执行期间持有，
 * 不会跨越任务中的 Redis 调用与后续更新。
 */
@Mapper
public interface JobLockMapper {

    @Update("UPDATE sys_job_lock "
            + "SET locked_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), "
            + "    holder = #{holder}, update_time = NOW() "
            + "WHERE job_name = #{jobName} "
            + "  AND (locked_until IS NULL OR locked_until < NOW())")
    int tryAcquire(@Param("jobName") String jobName,
                   @Param("holder") String holder,
                   @Param("leaseSeconds") int leaseSeconds);
}
