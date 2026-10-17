package com.example.order.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要记录操作日志的写操作接口，由 {@code OperLogAspect} 自动落库。
 * 查询接口不加此注解 —— 列表与详情每次请求都记会让日志表迅速膨胀。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块名，如 用户管理 */
    String module();

    /** 动作类型，取 {@link AuditAction} 中的常量 */
    String action();

    /** 操作说明，如 新增用户 */
    String description();
}
