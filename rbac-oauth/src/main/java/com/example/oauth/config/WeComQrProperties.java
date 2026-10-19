package com.example.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信扫码登录二维码配置
 * <p>
 * 二维码内容不是企业微信的授权页，而是本服务的短链 /q/{code}；
 * 手机扫码后短链 302 到企业微信 snsapi_base 授权码流程。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wecom.qr")
public class WeComQrProperties {

    private boolean enabled = true;

    /** 二维码/短链有效期（秒） */
    private int ttlSeconds = 300;

    /** 手机确认后，电脑端完成登录的等待时间（秒） */
    private int confirmTtlSeconds = 120;

    /** 短链对外可访问的基地址，必须是手机能访问的地址 */
    private String publicBaseUrl;

    /** 短链路径前缀 */
    private String shortPath = "/q";
}
