package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 绑定外部身份（MVP 只支持企业微信）
 */
@Data
public class UserIdentityForm {

    /** wecom */
    private String provider;

    @NotBlank(message = "企业ID不能为空")
    private String corpId;

    @NotBlank(message = "企业微信 UserID 不能为空")
    private String externalUserId;

    private String unionId;

    private String remark;
}
