package com.example.rbac.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 角色授权：某个子系统下的菜单勾选结果
 */
@Data
public class RoleSubsystemMenusForm {

    @NotNull(message = "子系统不能为空")
    private Long subsystemId;

    private List<Long> menuIds;
}
