package com.example.oauth.entity;

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

    /** 所属组织节点ID（公司/分公司/部门） */
    private Long deptId;

    private String email;

    private String phone;

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
}
