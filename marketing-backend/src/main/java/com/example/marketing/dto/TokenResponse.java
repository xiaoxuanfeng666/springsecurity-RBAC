package com.example.marketing.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 返给前端的令牌
 * <p>
 * 授权服务返回的是下划线字段（access_token），这里转成本系统的驼峰约定，
 * 与 rbac-frontend 的接口形态保持一致。
 */
@Data
public class TokenResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Long expiresIn;

    private String scope;
}
