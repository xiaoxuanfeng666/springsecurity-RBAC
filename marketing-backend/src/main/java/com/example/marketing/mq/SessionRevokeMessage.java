package com.example.marketing.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 「会话被撤销」的 MQ 消息（与 rbac-oauth 的同名类字段一致，字段名即契约）
 * <p>
 * 本系统本地验签看不到 SSO 会话是否存活，靠这条消息把作废的 sid 写入 Redis 撤销集合。
 */
@Data
public class SessionRevokeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被撤销的会话标识：该 sid 签发的所有令牌立即失效 */
    private String sid;

    /** 会话所属用户名，仅供日志 */
    private String username;

    /** 撤销记录保留秒数，由 rbac-oauth 按客户端令牌有效期计算；缺省时用本地兜底值 */
    private Long ttlSeconds;

    /** 毫秒时间戳 */
    private Long timestamp;
}
