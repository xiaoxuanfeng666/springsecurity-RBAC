package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class DeptForm {

    private Long id;

    /** 父节点 ID，0 表示顶级 */
    private Long parentId;

    @NotBlank(message = "名称不能为空")
    private String deptName;

    /** 类型：1公司 2分公司 3部门 */
    @NotNull(message = "类型不能为空")
    private Integer type;

    private Integer sort;

    private String leader;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private Integer status;
}
