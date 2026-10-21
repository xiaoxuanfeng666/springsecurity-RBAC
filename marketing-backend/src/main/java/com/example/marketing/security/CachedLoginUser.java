package com.example.marketing.security;

import com.example.marketing.entity.MarketingUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 缓存的登录用户快照：用户行 + 角色 + 权限点
 * <p>
 * 等价于「按 username 查库 + 算权限」的最终结果，命中缓存后整个请求不再访问数据库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedLoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private MarketingUser user;

    private Set<String> roles;

    private Set<String> permissions;
}
