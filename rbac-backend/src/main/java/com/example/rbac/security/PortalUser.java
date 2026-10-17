package com.example.rbac.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 门户的登录主体
 * <p>
 * 由 {@link PortalTokenClient} 调 {@code /oauth/check_token} 换回来的身份。
 * <b>刻意不带任何权限</b>：门户只负责「列出子系统、发起授权、统一登出」，
 * 不参与 RBAC 的权限点判定（那是 {@link LoginUser} 的职责，也是管理端接口的职责）。
 * 两套登录态分开，门户令牌就绝不可能被拿去调管理端接口。
 */
@Data
@AllArgsConstructor
public class PortalUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名，令牌里的 {@code user_name} —— 也是 SSO 会话的键 */
    private String username;

    private String nickname;

    private Long userId;
}
