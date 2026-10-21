package com.example.marketing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 授权码模式登录请求：前端从回调地址上拿到 code 后交给后端换取令牌
 */
@Data
public class CodeLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "授权码不能为空")
    private String code;

    /** 与发起授权时下发的 state 必须一致，且只能使用一次 */
    @NotBlank(message = "授权状态不能为空")
    private String state;
}
