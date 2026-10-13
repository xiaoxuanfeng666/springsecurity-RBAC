package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rbac.common.BusinessException;
import com.example.rbac.common.ResultCode;
import com.example.rbac.dto.UserForm;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.example.rbac.security.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public Page<SysUser> page(long pageNum, long pageSize, String username, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        return userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
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
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        user.setRemark(form.getRemark());
        user.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        userMapper.insert(user);
        saveUserRoles(user.getId(), form.getRoleIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserForm form) {
        SysUser exist = getById(form.getId());
        SysUser user = new SysUser();
        user.setId(form.getId());
        user.setNickname(form.getNickname());
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

        // 若本次修改把用户禁用，立即失效其登录凭证
        if (form.getStatus() != null && form.getStatus() == 0) {
            tokenStore.removeSession(exist.getId());
            log.info("用户被禁用，已失效登录凭证: {}", exist.getUsername());
        }
    }

    /**
     * 启用/禁用用户。禁用即「离职」场景，立即失效登录凭证。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser exist = getById(id);
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);

        if (status != null && status == 0) {
            tokenStore.removeSession(id);
            log.info("用户被禁用，已立即失效登录凭证: {}", exist.getUsername());
        }
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
}
