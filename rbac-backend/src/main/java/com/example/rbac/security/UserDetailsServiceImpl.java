package com.example.rbac.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.entity.SysUser;
import com.example.rbac.mapper.SysMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;

/**
 * 用户信息加载
 * <p>
 * 用户信息、权限与角色统一以「登录快照」为准：登录时查库一次写入 Redis，
 * 之后每个请求直接从快照重建认证主体，不再访问数据库。
 * 角色授权、用户资料或用户角色变更时快照被失效，用户下一次请求重建，因此变更依然立即生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PermissionSnapshotStore snapshotStore;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        // 登录时实时查库；登录成功后由 AuthService 以该结果建立快照
        PermissionSnapshot snapshot = buildSnapshot(user);
        return new LoginUser(user, null, snapshot.getPermissions(), snapshot.getRoles());
    }

    /**
     * 按 userId 加载（供 JWT 过滤器使用）：完全取自会话快照，快照缺失时才查库重建。
     * 因此正常情况下每个请求都不会碰到 sys_user 表。
     */
    public LoginUser loadLoginUser(Long userId, String sessionId) {
        PermissionSnapshot snapshot = getOrBuildSnapshot(userId, sessionId);
        if (snapshot == null || snapshot.getUser() == null) {
            return null;
        }
        return new LoginUser(snapshot.getUser(), sessionId, snapshot.getPermissions(), snapshot.getRoles());
    }

    /**
     * 读会话快照；不存在（会话刚建立，或被资料/角色变更失效）则查库重建并写回
     */
    public PermissionSnapshot getOrBuildSnapshot(Long userId, String sessionId) {
        PermissionSnapshot cached = snapshotStore.get(sessionId);
        if (cached != null && cached.getUser() != null) {
            return cached;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        PermissionSnapshot rebuilt = buildSnapshot(user);
        snapshotStore.save(sessionId, rebuilt, jwtTokenProvider.getRefreshTokenExpireMillis());
        return rebuilt;
    }

    private PermissionSnapshot buildSnapshot(SysUser user) {
        return new PermissionSnapshot(user,
                new HashSet<>(menuMapper.selectPermsByUserId(user.getId())),
                new HashSet<>(userMapper.selectRoleKeysByUserId(user.getId())));
    }
}
