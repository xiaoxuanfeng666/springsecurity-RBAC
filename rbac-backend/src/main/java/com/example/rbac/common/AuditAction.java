package com.example.rbac.common;

/**
 * 操作日志动作类型（对应 sys_operation_log.action）
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
    /** 启用（停用/离职后恢复） */
    public static final String ENABLED = "ENABLED";
    /** 离职导致下线 */
    public static final String RESIGNED = "RESIGNED";
    /** 重置密码导致下线（管理员操作） */
    public static final String RESET_PWD = "RESET_PWD";
    /** 用户自助修改密码（不踢下线，仅留痕） */
    public static final String CHANGE_PWD = "CHANGE_PWD";

    /** 新增 */
    public static final String CREATE = "CREATE";
    /** 修改 */
    public static final String UPDATE = "UPDATE";
    /** 删除 */
    public static final String DELETE = "DELETE";
    /** 授权（分配角色/岗位/菜单权限） */
    public static final String GRANT = "GRANT";

    private AuditAction() {
    }
}
