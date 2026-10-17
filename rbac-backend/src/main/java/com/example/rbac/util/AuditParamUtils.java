package com.example.rbac.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.ui.Model;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 捕获接口请求参数，供审计日志上报使用
 * <p>
 * 从方法参数取值，而不是包装请求读取 body：controller 的写接口参数不是
 * {@code @RequestBody} DTO 就是 {@code @PathVariable}，方法参数已经覆盖；
 * 读原始流既需要额外过滤器，也会与 Spring 的反序列化抢流。
 * <p>
 * 敏感字段递归脱敏后再序列化，并按 audit_log.request_params 的容量截断。
 */
public final class AuditParamUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 与 audit_log.request_params TEXT 的使用约定保持一致 */
    private static final int MAX_LENGTH = 8000;

    private static final String MASK = "***";

    /**
     * key 命中这些片段（不区分大小写）即视为敏感字段。
     * 用包含而不是全等，是为了兼容 appSecret / oldPassword / refreshToken 这类命名。
     */
    private static final String[] SENSITIVE_KEY_PARTS = {
            "password", "pwd", "secret", "token", "authorization", "credential"
    };

    private AuditParamUtils() {
    }

    /**
     * @return 参数 JSON；没有可记录的业务参数时返回 null
     */
    public static String capture(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> values = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || isFrameworkObject(arg)) {
                continue;
            }
            try {
                values.add(MAPPER.convertValue(arg, Object.class));
            } catch (Exception e) {
                // 个别参数无法转成通用结构（如自定义流对象）时跳过，不能因为记日志影响业务
                values.add(String.valueOf(arg));
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        Object normalized = values.size() == 1 ? values.get(0) : values;
        String json;
        try {
            json = MAPPER.writeValueAsString(mask(normalized));
        } catch (Exception e) {
            return null;
        }
        return json.length() <= MAX_LENGTH ? json : json.substring(0, MAX_LENGTH);
    }

    /** 框架对象、认证对象不是业务参数，记进日志只有噪音 */
    private static boolean isFrameworkObject(Object arg) {
        return arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof HttpSession
                || arg instanceof WebRequest
                || arg instanceof BindingResult
                || arg instanceof Errors
                || arg instanceof MultipartFile
                || arg instanceof MultipartFile[]
                || arg instanceof Principal
                || arg instanceof Authentication
                || arg instanceof Model
                || arg instanceof Locale
                || arg instanceof TimeZone
                || arg instanceof UriComponentsBuilder;
    }

    private static Object mask(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<Object, Object> masked = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key != null && isSensitiveKey(key.toString())) {
                    masked.put(key, MASK);
                } else {
                    masked.put(key, mask(entry.getValue()));
                }
            }
            return masked;
        }
        if (value instanceof Collection) {
            List<Object> masked = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                masked.add(mask(item));
            }
            return masked;
        }
        if (value instanceof Object[]) {
            List<Object> masked = new ArrayList<>();
            for (Object item : (Object[]) value) {
                masked.add(mask(item));
            }
            return masked;
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
