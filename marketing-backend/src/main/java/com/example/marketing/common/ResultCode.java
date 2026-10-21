package com.example.marketing.common;

import lombok.Getter;

/**
 * 响应状态码
 * <p>
 * 从 2001 起编号，与 rbac-backend 的编号空间分开，避免两边看着像是同一套码。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "系统异常"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "登录已过期，请重新登录"),
    FORBIDDEN(403, "没有权限执行此操作"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    USERNAME_OR_PASSWORD_ERROR(2001, "用户名或密码错误"),
    ACCOUNT_DISABLED(2002, "账号已停用或已离职"),
    OAUTH_UNAVAILABLE(2003, "授权服务暂时不可用，请稍后重试"),
    USER_NOT_SYNCED(2004, "账号尚未同步到营销系统，请稍后重试"),
    CODE_INVALID(2005, "授权码无效或已过期，请重新授权"),
    REFRESH_TOKEN_INVALID(2006, "刷新凭证无效，请重新登录"),
    ACTIVITY_CODE_EXISTS(2007, "活动编码已存在"),
    ACTIVITY_NOT_EXIST(2008, "抽奖活动不存在"),
    TASK_CODE_EXISTS(2009, "任务编码已存在"),
    TASK_NOT_EXIST(2010, "积分任务不存在"),
    PRIZE_NOT_EXIST(2011, "奖品不存在"),
    PRIZE_IN_USE(2012, "奖品已被活动使用，无法删除"),
    ACTIVITY_PRIZE_INVALID(2013, "奖品概率之和不合法（需大于 0 且不超过 100%）"),
    STATE_INVALID(2020, "登录状态已失效或已被使用，请重新发起登录");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
