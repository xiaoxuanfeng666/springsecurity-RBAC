package com.example.audit.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * @PreAuthorize 鉴权失败：需显式处理，否则会被下方兜底处理器吞成 500
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : ResultCode.PARAM_ERROR.getMessage();
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 请求体读不出来：JSON 语法错误、字段类型对不上（"status": "abc"）、
     * 数字超出字段类型、日期格式不符合 {@code yyyy-MM-dd HH:mm:ss} 等。
     * <p>
     * 必须在兜底处理器之前接住，否则会返回 500「系统异常」——
     * 那是在说「服务出问题了」，而实际原因是请求参数不对，排查方向完全被带偏。
     * 不回显底层消息：Jackson 的报错里带着类的全限定名与内部字段路径，对调用方没有价值。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "请求参数格式不正确");
    }

    /**
     * 请求方法不对（例如把只有 POST 的接口按 GET 调）。
     * <p>
     * 与上一条同理：不接住的话会被兜底处理器吞成 500「系统异常」，
     * 而框架本来给的语义是 405 —— 调用方一眼就能看出是自己用错了方法。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(ResultCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.ERROR);
    }
}
