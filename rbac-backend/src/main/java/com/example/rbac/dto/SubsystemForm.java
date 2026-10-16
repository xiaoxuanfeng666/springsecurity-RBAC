package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class SubsystemForm {

    private Long id;

    @NotBlank(message = "子系统编码不能为空")
    private String subsystemCode;

    @NotBlank(message = "子系统名称不能为空")
    private String subsystemName;

    private Integer sort;

    private Integer status;

    private String remark;
}
