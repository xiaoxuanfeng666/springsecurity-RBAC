package com.example.rbac.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

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

    private String email;

    private String phone;

    private String avatar;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableLogic
    @JsonIgnore
    private Integer delFlag;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
