package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 客户端新增/修改表单
 * <p>
 * 客户端密钥不允许为空。新增时必填（由 {@code ClientDetailsService#create} 校验）；
 * 修改时留空表示保持原密钥不变 —— 列表不回显密钥，否则每次编辑都要重填。
 * 因此这里不加 {@code @NotBlank}：它无法区分「新增」与「修改」两种语义。
 */
@Data
public class ClientForm {

    @NotBlank(message = "客户端标识不能为空")
    private String appId;

    private String resourceIds;

    /** 新增必填（不允许为空）；修改留空表示保持原密钥不变 */
    private String appSecret;

    private String scope;

    @NotBlank(message = "授权模式不能为空")
    private String grantTypes;

    private String redirectUrl;

    private String authorities;

    private Integer accessTokenValidity;

    private Integer refreshTokenValidity;

    private String additionalInformation;

    private String autoApproveScopes;
}
