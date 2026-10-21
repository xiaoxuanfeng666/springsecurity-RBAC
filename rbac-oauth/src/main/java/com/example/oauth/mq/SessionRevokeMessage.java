package com.example.oauth.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 「会话被撤销」的 MQ 消息
 * <p>
 * 子系统改为本地验签后，不再每请求回源 check_token，也就看不到「会话是否还在」。
 * 为此本服务在销毁会话时把被作废的 {@code sid} 广播出去，子系统写入自己的 Redis 撤销集合，
 * 验签命中即拒。撤权粒度是 sid（一次浏览器登录一个 sid），与「新登录踢掉旧登录」的语义完全一致。
 * <p>
 * 各子系统各有一份同名字段的本类 —— 字段名即契约。
 */
@Data
public class SessionRevokeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被撤销的会话标识（令牌的 sid 声明）：该 sid 签发的所有令牌立即失效 */
    private String sid;

    /** 会话所属用户名，仅供日志与对账 */
    private String username;

    /**
     * 撤销记录应保留的秒数。
     * <p>
     * 必须覆盖「该会话可能签发出的、有效期最长的访问令牌」的剩余寿命，
     * 由 rbac-oauth 按会话涉及客户端的 {@code access_token_validity} 计算；
     * 固定写死会在客户端令牌有效期大于该值时提前丢失撤销记录，导致已撤销令牌重获放行。
     */
    private Long ttlSeconds;

    /** 毫秒时间戳 */
    private Long timestamp;
}
