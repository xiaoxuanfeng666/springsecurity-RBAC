package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.List;

@Data
public class UserForm {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 新增必填；修改时为空表示不改密码 */
    private String password;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 状态：1启用 0禁用 */
    private Integer status;

    private String remark;

    /** 分配的角色 ID */
    private List<Long> roleIds;
}
