package com.example.order.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 刷新令牌请求
 */
@Data
public class RefreshRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "刷新凭证不能为空")
    private String refreshToken;
}
