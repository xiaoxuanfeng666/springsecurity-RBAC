package com.example.oauth.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 「强制下线用户」的 MQ 消息
 * <p>
 * 由 rbac-backend 在管理员点「强制下线」时发出，rbac-oauth 消费后删掉该用户的 Redis 会话。
 * 两个服务是两个独立工程，没有共享模块，所以这个类在两边各有一份 —— 字段名即契约。
 */
@Data
public class SsoKickMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目前只有 KICK，留着便于以后扩展（例如 EXPIRE） */
    public static final String TYPE_KICK = "KICK";

    private String type;

    /** 被下线的会话记录 id（sys_sso_session.id），便于日志对账 */
    private Long sessionId;

    private Long userId;

    private String username;

    private String reason;

    /** 毫秒时间戳 */
    private Long timestamp;
}
