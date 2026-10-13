package com.example.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 令牌返回：Access Token 用于访问接口，Refresh Token 仅用于调用 /auth/refresh 续期
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;

    private String refreshToken;

    /** Access Token 有效期（秒） */
    private Long expiresIn;
}
