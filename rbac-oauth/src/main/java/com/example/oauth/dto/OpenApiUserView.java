package com.example.oauth.dto;

import com.example.oauth.entity.SysUser;
import lombok.Data;

import java.io.Serializable;

/**
 * 开放接口对外暴露的用户视图
 * <p>
 * 刻意不直接返回 {@link SysUser} 实体：实体的字段（手机号、邮箱、备注、逻辑删除标记等）
 * 属于内部数据，第三方接口只暴露必要的最小集合。新增字段前请先确认是否真的需要对外。
 */
@Data
public class OpenApiUserView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    /** 状态：1 在职 / 0 停用 / 2 离职 */
    private Integer status;

    public static OpenApiUserView from(SysUser user) {
        OpenApiUserView view = new OpenApiUserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setStatus(user.getStatus());
        return view;
    }
}
