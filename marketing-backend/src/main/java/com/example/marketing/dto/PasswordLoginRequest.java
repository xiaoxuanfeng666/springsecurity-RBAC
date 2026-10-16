package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 密码模式登录请求
 * <p>
 * 用户名密码最终由 rbac-oauth 校验（密码模式的 grant_type=password），
 * 本系统不存密码、不校验密码。
 */
@Data
public class PasswordLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
