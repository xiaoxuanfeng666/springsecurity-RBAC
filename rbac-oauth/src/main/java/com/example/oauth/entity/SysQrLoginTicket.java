package com.example.oauth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业微信扫码登录短链票据
 */
@Data
@TableName("sys_qr_login_ticket")
public class SysQrLoginTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 短链码，二维码内容为 {publicBaseUrl}{shortPath}/{code} */
    private String code;

    private String provider;

    private String corpId;

    private String externalUserId;

    private String username;

    private String returnTo;

    private String status;

    /** 电脑端完成登录时校验，一次性 */
    private String loginCode;

    private String errorMsg;

    private LocalDateTime expireTime;

    private LocalDateTime confirmedTime;

    private LocalDateTime usedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
