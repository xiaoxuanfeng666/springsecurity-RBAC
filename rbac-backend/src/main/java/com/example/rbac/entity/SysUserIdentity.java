package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户外部身份绑定（sys_user_identity）
 * <p>
 * 与 rbac-oauth 共用同一张表。provider + corp_id + external_user_id 唯一。
 */
@Data
@TableName("sys_user_identity")
public class SysUserIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** wecom / dingtalk / oidc */
    private String provider;

    private String corpId;

    private String externalUserId;

    private String unionId;

    /** 1有效 0解绑 */
    private Integer status;

    private LocalDateTime bindTime;

    private LocalDateTime lastLoginTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
