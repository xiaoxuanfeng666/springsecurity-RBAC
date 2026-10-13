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

    /** 所属组织节点ID（公司/分公司/部门） */
    private Long deptId;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 状态：1在职 0停用 2离职 */
    private Integer status;

    private String remark;

    /** 分配的角色 ID */
    private List<Long> roleIds;

    /** 分配的岗位 ID */
    private List<Long> postIds;
}
