package com.example.rbac.common;

/**
 * 审计动作类型（对应 sys_login_audit.action）
 */
public final class AuditAction {

    /** 登录 */
    public static final String LOGIN = "LOGIN";
    /** 登出 */
    public static final String LOGOUT = "LOGOUT";
    /** 管理员强制下线 */
    public static final String KICK = "KICK";
    /** 停用导致下线 */
    public static final String DISABLED = "DISABLED";
    /** 离职导致下线 */
    public static final String RESIGNED = "RESIGNED";
    /** 重置密码导致下线（管理员操作） */
    public static final String RESET_PWD = "RESET_PWD";
    /** 用户自助修改密码（不踢下线，仅留痕） */
    public static final String CHANGE_PWD = "CHANGE_PWD";

    private AuditAction() {
    }
}
