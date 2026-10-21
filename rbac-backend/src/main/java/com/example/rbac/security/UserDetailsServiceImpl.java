package com.example.rbac.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户信息加载
 * <p>
 * 登录态来自 rbac-oauth 的令牌，本服务只负责把令牌里的 {@code user_name} 还原成
 * 「用户 + 权限 + 角色」，并缓存为权限快照：正常情况下每个请求只读一次 Redis，不访问数据库。
 * 角色授权、用户资料或用户角色变更时快照被失效，用户下一次请求重建，因此变更依然立即生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl {

    /** 快照有效期：与刷新令牌 / SSO 会话的 7 天对齐 */
    private static final long SNAPSHOT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PermissionSnapshotStore snapshotStore;

    /**
     * 按 username 加载认证主体（供令牌过滤器使用）：优先读快照，缺失时查库重建。
     * 用户不存在或已停用/离职时返回 null，由认证入口点统一回 401。
     */
    public LoginUser loadLoginUser(String username) {
        PermissionSnapshot snapshot = getOrBuildSnapshot(username);
        if (snapshot == null || snapshot.getUser() == null) {
            return null;
        }
        SysUser user = snapshot.getUser();
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return new LoginUser(user, snapshot.getPermissions(), snapshot.getRoles());
    }

    /**
     * 读权限快照；不存在（首次登录，或被资料/角色变更失效）则查库重建并写回
     */
    public PermissionSnapshot getOrBuildSnapshot(String username) {
        PermissionSnapshot cached = snapshotStore.get(username);
        if (cached != null && cached.getUser() != null) {
            return cached;
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            return null;
        }
        PermissionSnapshot rebuilt = buildSnapshot(user);
        snapshotStore.save(username, rebuilt, SNAPSHOT_TTL_MILLIS);
        return rebuilt;
    }

    private PermissionSnapshot buildSnapshot(SysUser user) {
        Set<String> roleKeys = new HashSet<>(userMapper.selectRoleKeysByUserId(user.getId()));
        // 超级管理员的权限不查库：直接给通配符，新增菜单后无需手动补授权
        Set<String> permissions = SuperAdmin.matches(roleKeys)
                ? new HashSet<>(Collections.singletonList(SuperAdmin.WILDCARD_PERMISSION))
                : new HashSet<>(menuMapper.selectPermsByUserId(user.getId()));
        return new PermissionSnapshot(user, permissions, roleKeys);
    }
}
