package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "刷新凭证不能为空")
    private String refreshToken;
}
