package com.example.rbac.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 「强制下线用户」的 MQ 消息
 * <p>
 * 由本服务在管理员停用/离职/删除/重置密码/强制下线时发出，rbac-oauth 消费后删掉该用户的 Redis 会话。
 * 两个服务是两个独立工程，没有共享模块，所以这个类在两边各有一份 —— 字段名即契约，
 * 改动时需与 {@code com.example.oauth.mq.SsoKickMessage} 同步。
 */
@Data
public class SsoKickMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 管理员手动「强制下线」 */
    public static final String TYPE_KICK = "KICK";
    /** 账号被停用 */
    public static final String TYPE_DISABLED = "DISABLED";
    /** 账号被标记离职 */
    public static final String TYPE_RESIGNED = "RESIGNED";
    /** 账号被删除 */
    public static final String TYPE_DELETE = "DELETE";
    /** 管理员重置密码 */
    public static final String TYPE_RESET_PWD = "RESET_PWD";

    private String type;

    /** 被下线的会话记录 id（sys_sso_session.id），便于日志对账 */
    private Long sessionId;

    private Long userId;

    private String username;

    private String reason;

    /** 毫秒时间戳 */
    private Long timestamp;
}
