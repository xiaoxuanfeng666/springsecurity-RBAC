package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PostForm {

    private Long id;

    @NotBlank(message = "岗位编码不能为空")
    private String postCode;

    @NotBlank(message = "岗位名称不能为空")
    private String postName;

    private Integer sort;

    private Integer status;

    private String remark;
}
