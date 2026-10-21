package com.example.oauth.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一门户的 SSO 会话（存 Redis，键 {@code sso:session:{username}}）
 * <p>
 * 一个用户在同一时刻只有一个 SSO 会话，会话上记录他这次登录进过哪些客户端。
 * 令牌能否使用取决于这个会话是否存在 —— 详见 {@link SsoSessionStore}。
 * <p>
 * <b>字段名是与 rbac-backend 的契约</b>：那边的「SSO 会话」管理页会反序列化同一份 JSON，
 * 两边改动需同步（本服务与 rbac-backend 是两个独立工程，没有共享模块）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话主键，与 Redis 键后缀一致 */
    private String username;

    /**
     * 当前活跃的浏览器会话标识（JWT 的 sid 声明）。
     * <p>
     * 浏览器登录时它就是 {@code oauth2:auth:{token}} 的 token；密码模式直接取令牌时
     * 是服务端生成的随机值。新登录会轮换它，旧浏览器令牌的 sid 随即对不上。
     */
    private String sessionId;

    /** 仅供管理端展示与按 userId 踢人时比对；不参与令牌校验 */
    private Long userId;

    private String nickname;

    /** 登录时间，已格式化好的 {@code yyyy-MM-dd HH:mm:ss} —— 用字符串存，免去两个服务的时区/格式配置差异 */
    private String loginTime;

    /**
     * 会话绝对过期时间（epoch 秒），登录时写入，之后续期（touch）不会延长。
     * <p>
     * Redis 里的实际 TTL 取「空闲超时」与「距该时刻的剩余时间」的较小值，
     * 因此会话最长不会超过配置的绝对上限。
     */
    private Long expireAt;

    /** 本次会话登录过的客户端 id 列表，如 [portal, order-center, marketing] */
    private List<String> clients;

    public void addClient(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            return;
        }
        if (clients == null) {
            clients = new ArrayList<>();
        }
        if (!clients.contains(clientId)) {
            clients.add(clientId);
        }
    }
}
