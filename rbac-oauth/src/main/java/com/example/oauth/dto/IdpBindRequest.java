package com.example.oauth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 自助绑定：用本地账号密码确认身份
 */
@Data
public class IdpBindRequest {

    @NotBlank(message = "绑定凭据不能为空")
    private String ticket;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
