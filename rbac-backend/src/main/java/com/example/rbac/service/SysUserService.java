package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.AuditAction;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.UserForm;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserPost;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mapper.SysUserPostMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.example.rbac.mq.SsoKickPublisher;
import com.example.rbac.mq.SubsystemSyncService;
import com.example.rbac.security.PermissionSnapshotStore;
import com.example.rbac.security.TokenStore;
import com.example.rbac.util.UniqueKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    /** 状态取值：1 在职 / 0 停用 / 2 离职 */
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_RESIGNED = 2;

    /** 操作日志模块名 */
    private static final String MODULE_USER = "用户管理";

    /** sys_user.username 列长度 */
    private static final int USERNAME_MAX_LENGTH = 64;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final PermissionSnapshotStore snapshotStore;
    private final SysOperationLogService logService;
    private final SubsystemSyncService subsystemSyncService;
    private final SsoKickPublisher ssoKickPublisher;

    public Page<SysUser> page(long pageNum, long pageSize, String username, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillPostIds(page.getRecords());
        return page;
    }

    /**
     * 批量填充用户岗位 ID，避免逐条查询
     */
    private void fillPostIds(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, List<Long>> postsByUser = userPostMapper.selectList(
                        new LambdaQueryWrapper<SysUserPost>().in(SysUserPost::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(SysUserPost::getUserId,
                        Collectors.mapping(SysUserPost::getPostId, Collectors.toList())));
        users.forEach(u -> u.setPostIds(postsByUser.getOrDefault(u.getId(), new ArrayList<>())));
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        return user;
    }

    public List<Long> getRoleIds(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    public List<Long> getPostIds(Long userId) {
        return userPostMapper.selectList(
                        new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, userId))
                .stream().map(SysUserPost::getPostId).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(UserForm form) {
        if (!StringUtils.hasText(form.getPassword())) {
            throw new BusinessException("新增用户时密码不能为空");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, form.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(form.getUsername());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setNickname(form.getNickname());
        user.setDeptId(form.getDeptId());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setRemark(form.getRemark());
        user.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        userMapper.insert(user);
        saveUserRoles(user.getId(), form.getRoleIds());
        saveUserPosts(user.getId(), form.getPostIds());
        // 只把该用户同步到其角色有授权的子系统
        subsystemSyncService.syncForRoles(form.getRoleIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserForm form) {
        SysUser exist = getById(form.getId());
        // 受影响子系统 = 旧角色 ∪ 新角色：旧角色所在的子系统要把用户移除，
        // 新角色所在的子系统要把用户加进去
        List<Long> affectedRoleIds = new ArrayList<>(getRoleIds(form.getId()));
        if (form.getRoleIds() != null) {
            affectedRoleIds.addAll(form.getRoleIds());
        }
        SysUser user = new SysUser();
        user.setId(form.getId());
        user.setNickname(form.getNickname());
        user.setDeptId(form.getDeptId());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setRemark(form.getRemark());
        if (form.getStatus() != null) {
            user.setStatus(form.getStatus());
        }
        // 密码为空则不修改
        if (StringUtils.hasText(form.getPassword())) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        userMapper.updateById(user);

        if (form.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, form.getId()));
            saveUserRoles(form.getId(), form.getRoleIds());
        }

        if (form.getPostIds() != null) {
            userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>()
                    .eq(SysUserPost::getUserId, form.getId()));
            saveUserPosts(form.getId(), form.getPostIds());
        }

        // 若本次修改把用户停用或标记离职，立即失效其登录凭证
        if (form.getStatus() != null && isOffline(form.getStatus())) {
            offlineUser(exist, form.getStatus());
        }

        // 用户资料与角色都在登录快照里，改完必须失效，否则昵称/部门等要等重新登录才更新
        snapshotStore.invalidateUser(form.getId());
        subsystemSyncService.syncForRoles(affectedRoleIds);
    }

    /**
     * 启用/停用/离职用户。停用与离职都会立即失效登录凭证，把已登录用户踢下线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser exist = getById(id);
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);

        if (status != null && isOffline(status)) {
            offlineUser(exist, status);
        } else if (status != null && status == STATUS_ENABLED) {
            logService.record(MODULE_USER, AuditAction.ENABLED, exist.getId(),
                    exist.getUsername(), "启用账号");
        }
        // 状态变化只影响该用户所属角色对应的子系统
        subsystemSyncService.syncForRoles(getRoleIds(id));
    }

    /** 停用(0)与离职(2)都会让用户立即下线 */
    private boolean isOffline(int status) {
        return status == 0 || status == STATUS_RESIGNED;
    }

    /**
     * 停用/离职导致的下线：删除本系统会话、通知授权服务删除 SSO 会话，并写入审计
     * <p>
     * 只删本系统的 Redis 会话不够 —— 用户在订单中心、营销系统等子系统手里的令牌仍会有效，
     * 必须由 rbac-oauth 删掉 SSO 会话，各子系统的下一次 check_token 才会失败。
     */
    private void offlineUser(SysUser user, int status) {
        boolean resigned = status == STATUS_RESIGNED;
        tokenStore.removeSession(user.getId());
        ssoKickPublisher.publish(
                resigned ? SsoKickPublisher.TYPE_RESIGNED : SsoKickPublisher.TYPE_DISABLED,
                null, user.getId(), user.getUsername(),
                resigned ? "账号已离职" : "账号已停用");
        logService.record(MODULE_USER, resigned ? AuditAction.RESIGNED : AuditAction.DISABLED,
                user.getId(), user.getUsername(),
                resigned ? "标记离职，强制下线" : "停用，强制下线");
        log.info("用户被{}，已立即失效登录凭证: {}", resigned ? "标记离职" : "停用", user.getUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser exist = getById(id);
        if ("admin".equals(exist.getUsername())) {
            throw new BusinessException("内置管理员账号不允许删除");
        }
        // 删除前先记下角色：之后要靠它们决定通知哪些子系统
        List<Long> roleIds = getRoleIds(id);
        // 先失效登录凭证，再删除数据
        tokenStore.removeSession(id);
        ssoKickPublisher.publish(SsoKickPublisher.TYPE_DELETE, null, id, exist.getUsername(), "账号已删除");
        releaseUsername(exist);
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        log.info("用户已删除并失效登录凭证: {}", exist.getUsername());
        subsystemSyncService.syncForRoles(roleIds);
    }

    /**
     * 腾出 username 唯一键：逻辑删除后行仍在表中，不改名同名用户就再也建不出来
     */
    private void releaseUsername(SysUser user) {
        SysUser renamed = new SysUser();
        renamed.setId(user.getId());
        renamed.setUsername(
                UniqueKeyUtils.release(user.getUsername(), user.getId(), USERNAME_MAX_LENGTH));
        userMapper.updateById(renamed);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long userId, List<Long> roleIds) {
        getById(userId);
        // 旧角色所在的子系统要移除该用户，新角色所在的子系统要加入
        List<Long> affectedRoleIds = new ArrayList<>(getRoleIds(userId));
        if (roleIds != null) {
            affectedRoleIds.addAll(roleIds);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        saveUserRoles(userId, roleIds);
        // 角色变了，权限快照立即失效，下一次请求重建
        snapshotStore.invalidateUser(userId);
        subsystemSyncService.syncForRoles(affectedRoleIds);
    }

    /**
     * 管理员重置他人密码：立即失效其登录凭证，该用户需用新密码重新登录。
     * <p>
     * 与「自助修改密码」（{@code PUT /auth/password}）相对 —— 本人改密保留当前会话。
     */
    public void resetPassword(Long userId, String password) {
        SysUser user = getById(userId);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(update);
        // 重置密码后强制重新登录（本系统会话 + 所有子系统的 SSO 会话）
        tokenStore.removeSession(userId);
        ssoKickPublisher.publish(SsoKickPublisher.TYPE_RESET_PWD, null, userId, user.getUsername(), "密码已重置");
        logService.record(MODULE_USER, AuditAction.RESET_PWD, userId, user.getUsername(), "重置密码，强制下线");
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private void saveUserPosts(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            SysUserPost userPost = new SysUserPost();
            userPost.setUserId(userId);
            userPost.setPostId(postId);
            userPostMapper.insert(userPost);
        }
    }

    /**
     * 重置用户岗位（多对多）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePosts(Long userId, List<Long> postIds) {
        getById(userId);
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, userId));
        saveUserPosts(userId, postIds);
    }
}
