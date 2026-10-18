package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
public class SubsystemForm {

    private Long id;

    /**
     * 子系统编码同时用作 MQ 的 tag，只能包含字母、数字、下划线和短横线
     */
    @NotBlank(message = "子系统编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "子系统编码只能包含字母、数字、下划线和短横线")
    private String subsystemCode;

    @NotBlank(message = "子系统名称不能为空")
    private String subsystemName;

    private Integer sort;

    private Integer status;

    private String remark;
}
