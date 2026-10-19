package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码不参与 JSON 序列化，避免返回给前端 */
    @JsonIgnore
    private String password;

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

    private String email;

    /** 企业邮箱 */
    private String bizMail;

    private String phone;

    /** 座机 */
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

    private String avatar;

    /** 状态：1在职 0停用 2离职（停用与离职都会立即失效登录凭证） */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 用户已分配的岗位 ID，非数据库字段，列表查询时批量填充 */
    @TableField(exist = false)
    private List<Long> postIds;

    /** 用户所属全部部门 ID（含主部门），非数据库字段 */
    @TableField(exist = false)
    private List<Long> deptIds;

    /** 主部门是否负责人，非数据库字段 */
    @TableField(exist = false)
    private Boolean leaderInDept;

    /** 主部门内排序，非数据库字段 */
    @TableField(exist = false)
    private Integer orderInDept;
}
