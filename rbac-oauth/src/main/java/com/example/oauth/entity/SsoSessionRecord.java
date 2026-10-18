package com.example.oauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SSO 登录会话记录（表 {@code sys_sso_session}，存放在 rbac 库）
 * <p>
 * Redis 里的 {@code sso:session:{username}} 只是「令牌能不能用」的开关 —— 没有历史、没有设备信息，
 * 也没法审计。这张表补上这部分：每次浏览器登录落一行，登出/被踢时更新状态。
 * <p>
 * <b>与 rbac-backend 是同一张表</b>：那边读它做「SSO 会话」管理页、并按行强制下线；
 * 字段名变更需两边同步（两个独立工程，没有共享模块）。
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

    /** 登录 IP（X-Forwarded-For 优先，便于反向代理下取到真实来源） */
    private String loginIp;

    /** 原始 User-Agent */
    private String userAgent;

    /** 解析后的设备与浏览器，如 "Chrome / Windows" */
    private String device;

    /** 登录方式：PASSWORD / WECOM */
    private String loginType;

    private LocalDateTime loginTime;

    private LocalDateTime logoutTime;

    /** 1 在线 / 0 已登出 / 2 被强制下线 */
    private Integer status;

    /** 登出原因：主动登出 / 被强制下线 / 被新登录顶替 / 会话过期 */
    private String logoutReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
