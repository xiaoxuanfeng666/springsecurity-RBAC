package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SSO 登录会话记录（表 {@code sys_sso_session}，与 rbac-oauth 共用同一张表）
 * <p>
 * 写入由 rbac-oauth 负责（它才看得到用户的浏览器：IP、User-Agent）；本服务只读它做「SSO 会话」列表，
 * 并在「强制下线」时把状态改成 2 —— 真正的令牌失效由 rbac-oauth 消费 MQ 后删 Redis 会话完成。
 * <p>
 * 字段名与 rbac-oauth 的 {@code SsoSessionRecord} 一一对应，两边改动需同步。
 */
@Data
@TableName("sys_sso_session")
public class SsoSessionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 在线 */
    public static final int STATUS_ONLINE = 1;
    /** 已登出 */
    public static final int STATUS_OFFLINE = 0;
    /** 被管理员强制下线 */
    public static final int STATUS_KICKED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private String loginIp;

    private String userAgent;

    /** 解析后的设备与浏览器，如 "Chrome / Windows" */
    private String device;

    private LocalDateTime loginTime;

    private LocalDateTime logoutTime;

    /** 1 在线 / 0 已登出 / 2 被强制下线 */
    private Integer status;

    private String logoutReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
