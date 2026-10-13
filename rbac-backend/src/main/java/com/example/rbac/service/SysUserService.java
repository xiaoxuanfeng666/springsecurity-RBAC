package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.UserForm;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserPost;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mapper.SysUserPostMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.example.rbac.security.PermissionSnapshotStore;
import com.example.rbac.security.TokenStore;
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
    private static final int STATUS_RESIGNED = 2;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final PermissionSnapshotStore snapshotStore;

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
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserForm form) {
        SysUser exist = getById(form.getId());
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
            // 角色变了，权限快照立即失效
            snapshotStore.invalidateUser(form.getId());
        }

        if (form.getPostIds() != null) {
            userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>()
                    .eq(SysUserPost::getUserId, form.getId()));
            saveUserPosts(form.getId(), form.getPostIds());
        }

        // 若本次修改把用户停用或标记离职，立即失效其登录凭证
        if (form.getStatus() != null && isOffline(form.getStatus())) {
            tokenStore.removeSession(exist.getId());
            log.info("用户被{}，已立即失效登录凭证: {}", statusText(form.getStatus()), exist.getUsername());
        }
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
            tokenStore.removeSession(id);
            log.info("用户被{}，已立即失效登录凭证: {}", statusText(status), exist.getUsername());
        }
    }

    /** 停用(0)与离职(2)都会让用户立即下线 */
    private boolean isOffline(int status) {
        return status == 0 || status == STATUS_RESIGNED;
    }

    private String statusText(int status) {
        return status == STATUS_RESIGNED ? "标记离职" : "停用";
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser exist = getById(id);
        if ("admin".equals(exist.getUsername())) {
            throw new BusinessException("内置管理员账号不允许删除");
        }
        // 先失效登录凭证，再删除数据
        tokenStore.removeSession(id);
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        log.info("用户已删除并失效登录凭证: {}", exist.getUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long userId, List<Long> roleIds) {
        getById(userId);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        saveUserRoles(userId, roleIds);
        // 角色变了，权限快照立即失效，下一次请求重建
        snapshotStore.invalidateUser(userId);
    }

    public void resetPassword(Long userId, String password) {
        getById(userId);
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.updateById(user);
        // 重置密码后强制重新登录
        tokenStore.removeSession(userId);
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
