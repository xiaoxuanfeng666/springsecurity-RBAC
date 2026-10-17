package com.example.rbac.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 「SSO 会话」管理页的一行 = 一个在线用户
 * <p>
 * 数据直接来自 Redis 里的 {@code sso:session:{username}}（由 rbac-oauth 写入），
 * 字段名与那边的 {@code SsoSession} 一一对应 —— 两边是两个独立工程，没有共享模块，
 * 靠 JSON 字段名对齐。
 */
@Data
public class SsoSessionView implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    private Long userId;

    private String nickname;

    /** 登录时间，形如 2026-10-16 14:30:00 */
    private String loginTime;

    /** 本次会话登录过的客户端，如 [portal, order-center, marketing] */
    private List<String> clients;
}
