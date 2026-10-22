package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 敏感操作二次验证：用户重新输入登录密码
 */
@Data
public class ElevateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "密码不能为空")
    private String password;
}
