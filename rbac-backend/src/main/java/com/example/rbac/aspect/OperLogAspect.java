package com.example.rbac.aspect;

import com.example.rbac.common.OperLog;
import com.example.rbac.service.SysOperationLogService;
import com.example.rbac.util.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面：拦截标注了 {@link OperLog} 的接口，记录成功与失败两种结果。
 * <p>
 * 切点落在 controller 方法上 —— 此时业务事务已提交，因此业务失败（异常向上抛出、
 * 由全局异常处理器转成响应）也能被记录下来，不会因为事务回滚把日志一并丢掉。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperationLogService logService;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            record(operLog, true, null, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            record(operLog, false, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void record(OperLog operLog, boolean success, String errorMsg, long durationMs) {
        logService.recordAction(operLog.module(), operLog.action(), operLog.description(),
                WebUtils.getRequestMethod(), WebUtils.getRequestUri(),
                success, errorMsg, durationMs);
    }
}
