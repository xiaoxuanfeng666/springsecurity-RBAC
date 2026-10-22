package com.example.marketing.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感操作：执行前要求用户重新输入登录密码
 * <p>
 * 用户通过 POST /auth/elevate 验证密码后，会话获得一段时间的「已二次验证」标记
 * （rbac.bff.elevate-ttl-seconds，默认 5 分钟）。会话被借用时，
 * 攻击者也无法完成这些操作。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveOperation {
}
