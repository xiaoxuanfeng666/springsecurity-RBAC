package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RoleForm {

    private Long id;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @NotBlank(message = "角色标识不能为空")
    private String roleKey;

    private Integer sort;

    private Integer status;

    private String remark;
}
