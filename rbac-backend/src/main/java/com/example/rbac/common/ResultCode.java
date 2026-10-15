package com.example.rbac.common;

import lombok.Getter;

/**
 * 响应状态码
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "系统异常"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "登录已过期，请重新登录"),
    FORBIDDEN(403, "没有权限执行此操作"),
    NOT_FOUND(404, "资源不存在"),

    USERNAME_OR_PASSWORD_ERROR(1001, "用户名或密码错误"),
    ACCOUNT_DISABLED(1002, "账号已停用，请联系管理员"),
    USER_NOT_EXIST(1003, "用户不存在"),
    TOKEN_INVALID(1004, "Token 无效或已过期"),
    TOKEN_EXPIRED(1005, "Token 已过期"),
    SESSION_INVALID(1006, "会话已失效，请重新登录"),
    REFRESH_TOKEN_INVALID(1007, "刷新凭证无效，请重新登录"),
    USERNAME_EXISTS(1008, "用户名已存在"),
    ROLE_KEY_EXISTS(1009, "角色标识已存在"),
    ROLE_IN_USE(1010, "角色已被用户使用，无法删除"),
    MENU_HAS_CHILDREN(1011, "存在子菜单，无法删除"),
    DEPT_HAS_CHILDREN(1012, "存在下级节点，无法删除"),
    DEPT_IN_USE(1013, "该节点下存在用户，无法删除"),
    DEPT_NOT_EXIST(1014, "组织节点不存在"),
    POST_CODE_EXISTS(1015, "岗位编码已存在"),
    POST_IN_USE(1016, "岗位已被用户使用，无法删除"),
    POST_NOT_EXIST(1017, "岗位不存在"),
    RESIGNED(1018, "该用户已离职"),
    KICK_SELF(1019, "不能强制下线当前登录的账号"),
    OLD_PASSWORD_ERROR(1020, "原密码不正确"),
    NEW_PASSWORD_SAME(1021, "新密码不能与原密码相同"),
    CLIENT_ID_EXISTS(1022, "客户端标识已存在"),
    CLIENT_NOT_EXIST(1023, "客户端不存在"),
    CLIENT_SECRET_REQUIRED(1024, "客户端密钥不能为空");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
