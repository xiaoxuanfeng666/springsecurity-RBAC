package com.example.marketing.aspect;

import com.example.marketing.common.OperLog;
import com.example.marketing.mq.AuditLogMessage;
import com.example.marketing.mq.AuditLogPublisher;
import com.example.marketing.security.SecurityUtils;
import com.example.marketing.util.AuditParamUtils;
import com.example.marketing.util.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 审计日志切面：拦截标注了 {@link OperLog} 的增删改接口，上报到审计日志系统。
 * <p>
 * 切点落在 controller 方法上 —— 此时业务事务已提交，因此业务失败（异常向上抛出、
 * 由全局异常处理器转成响应）也能被上报，不会因为事务回滚把日志一并丢掉。
 * <p>
 * 登录、登出、强制下线、改密等动作不走这里：它们由业务代码直接写入
 * {@code sys_operation_log}，不属于「接口增删改」的审计范围。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final AuditLogPublisher auditLogPublisher;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        // 参数在方法执行前取：执行过程中 DTO 可能被业务代码改写
        String requestParams = AuditParamUtils.capture(joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            publish(operLog, requestParams, true, null, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            publish(operLog, requestParams, false, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void publish(OperLog operLog, String requestParams,
                         boolean success, String errorMsg, long durationMs) {
        AuditLogMessage message = new AuditLogMessage();
        message.setSource(AuditLogPublisher.SOURCE);
        message.setModule(operLog.module());
        message.setAction(operLog.action());
        message.setDescription(operLog.description());
        message.setOperatorId(SecurityUtils.getUserId());
        message.setOperatorName(SecurityUtils.getUsername());
        message.setHttpMethod(WebUtils.getRequestMethod());
        message.setRequestUri(WebUtils.getRequestUri());
        message.setRequestParams(requestParams);
        message.setSuccess(success ? 1 : 0);
        message.setErrorMsg(errorMsg);
        message.setDurationMs(durationMs);
        message.setIp(WebUtils.getClientIp());
        message.setTimestamp(System.currentTimeMillis());
        auditLogPublisher.publish(message);
    }
}
