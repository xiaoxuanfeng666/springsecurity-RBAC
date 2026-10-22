package com.example.marketing.security;

import com.example.marketing.common.BusinessException;
import com.example.marketing.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 敏感操作拦截器：会话没有「已二次验证」标记时直接拒绝
 * <p>
 * 只拦标注了 SensitiveOperation 的接口。返回的错误码由前端识别后弹出密码框，
 * 验证通过会重放原请求。
 */
@Component
@RequiredArgsConstructor
public class SensitiveOperationInterceptor implements HandlerInterceptor {

    private final BffSessionStore sessionStore;
    private final BffSessionCookie sessionCookie;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        boolean sensitive = method.getMethodAnnotation(SensitiveOperation.class) != null
                || method.getBeanType().isAnnotationPresent(SensitiveOperation.class);
        if (!sensitive) {
            return true;
        }
        BffSessionStore.ResolvedSession resolved = sessionStore.resolve(sessionCookie.read(request));
        if (resolved != null
                && resolved.getSession().getElevatedUntil() > System.currentTimeMillis()) {
            return true;
        }
        throw new BusinessException(ResultCode.REAUTH_REQUIRED);
    }
}
