package com.example.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户（同步表）
 * <p>
 * 由 RBAC 经 MQ 下发后重建，本系统不做增删改。id 沿用 RBAC 的 sys_user.id。
 * 本系统没有登录与用户管理：用户是谁由 rbac-oauth 签发的令牌决定。
 */
@Data
@TableName("order_user")
public class OrderUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 沿用 RBAC 的 sys_user.id，不是本库自增 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 登录名：OAuth2 令牌里唯一可靠的身份（令牌中只有 user_name） */
    private String username;

    private String nickname;

    /** 1在职 0停用 2离职 */
    private Integer status;

    /** 角色标识，逗号分隔，如 "admin" 或 "common,auditor" */
    private String roleKeys;

    private LocalDateTime syncTime;
}
