package com.example.rbac.outbound;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 出站推送的用户
 */
@Data
public class OutboundUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    /** 外部用户ID：已绑定时取绑定值，否则用本地用户名 */
    private String externalUserId;

    private String username;

    private String name;

    /** 工号 */
    private String employeeNo;

    /** 别名 */
    private String alias;

    /** 职务 */
    private String position;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    private String mobile;

    private String email;

    /** 企业邮箱 */
    private String bizMail;

    /** 座机 */
    private String telephone;

    /** 直属上级（逗号分隔） */
    private String directLeader;

    /** 对外职务 */
    private String externalPosition;

    /** 对外昵称 */
    private String externalNickname;

    /** 地址 */
    private String address;

    /** 扩展属性 JSON */
    private String extAttr;

    /** 主部门是否负责人 */
    private Boolean leaderInDept;

    /** 主部门内排序 */
    private Integer orderInDept;

    /** 外部部门ID（数字字符串），主部门排第一 */
    private List<String> departmentIds = new ArrayList<>();

    /** 本地部门ID；出站处理时用于解析外部部门ID */
    private List<Long> localDeptIds = new ArrayList<>();

    private boolean enabled = true;
}
