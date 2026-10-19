package com.example.rbac.directory;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部成员
 */
@Data
public class ExternalMember implements Serializable {

    private static final long serialVersionUID = 1L;

    private String externalUserId;

    private String name;

    /** 工号 */
    private String employeeNo;

    /** 别名 */
    private String alias;

    /** 职务 */
    private String position;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 企业邮箱 */
    private String bizMail;

    private String mobile;

    private String email;

    /** 座机 */
    private String telephone;

    /** 直属上级（外部 userid，多个逗号分隔） */
    private String directLeader;

    /** 对外职务 */
    private String externalPosition;

    /** 对外昵称 */
    private String externalNickname;

    /** 地址 */
    private String address;

    /** 扩展属性 JSON */
    private String extAttr;

    /** 主部门的外部ID */
    private String mainDepartmentId;

    /** 是否部门负责人（主部门） */
    private boolean leaderInDept;

    /** 部门内排序（主部门） */
    private int orderInDept;

    private List<String> departmentIds = new ArrayList<>();

    private boolean active = true;
}
