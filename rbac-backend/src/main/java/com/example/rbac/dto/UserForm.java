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

    /** 工号 */
    private String employeeNo;

    /** 别名 */
    private String alias;

    /** 职务 */
    private String position;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 主部门ID（公司/分公司/部门） */
    private Long deptId;

    /** 全部部门 ID（含主部门）；为空时只使用 deptId */
    private List<Long> deptIds;

    /** 主部门是否负责人 */
    private Boolean leaderInDept;

    /** 主部门内排序 */
    private Integer orderInDept;

    @Email(message = "邮箱格式不正确")
    private String email;

    /** 企业邮箱 */
    @Email(message = "企业邮箱格式不正确")
    private String bizMail;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 座机 */
    @Pattern(regexp = "^$|^[0-9+\\-()\\s]{1,32}$", message = "座机格式不正确")
    private String telephone;

    /** 直属上级（外部 userid / 用户名，多个逗号分隔） */
    private String directLeader;

    /** 对外职务 */
    private String externalPosition;

    /** 对外昵称 */
    private String externalNickname;

    /** 地址 */
    private String address;

    /** 扩展属性（JSON） */
    private String extAttr;

    /** 状态：1在职 0停用 2离职 */
    private Integer status;

    private String remark;

    /** 分配的角色 ID */
    private List<Long> roleIds;

    /** 分配的岗位 ID */
    private List<Long> postIds;
}
