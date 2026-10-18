package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.config.DirectorySyncProperties;
import com.example.rbac.directory.DirectoryProvider;
import com.example.rbac.directory.DirectoryProviderRegistry;
import com.example.rbac.directory.ExternalDepartment;
import com.example.rbac.directory.ExternalMember;
import com.example.rbac.directory.WeComDirectoryProvider;
import com.example.rbac.entity.SysDept;
import com.example.rbac.entity.SysDeptIdentity;
import com.example.rbac.entity.SysRole;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserIdentity;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysDeptIdentityMapper;
import com.example.rbac.mapper.SysDeptMapper;
import com.example.rbac.mapper.SysRoleMapper;
import com.example.rbac.mapper.SysUserIdentityMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.example.rbac.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部通讯录 / 部门同步
 * <p>
 * 部门：按 external_dept_id 映射到本地 sys_dept（sys_dept_identity），
 * 新部门挂在配置的 rootDeptId 下；成员：已绑定用户更新资料与状态，
 * 未绑定用户按配置自动建号（create-users），并写入 sys_user_identity。
 * <p>
 * 只同步「组织与用户」，不处理角色权限（默认角色由配置指定）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorySyncService {

    private static final int MAX_DEPT_PASSES = 10;
    private static final int MAX_USERNAME_LENGTH = 50;

    private final DirectorySyncProperties properties;
    private final DirectoryProviderRegistry providerRegistry;
    private final SysDeptMapper deptMapper;
    private final SysDeptIdentityMapper deptIdentityMapper;
    private final SysUserMapper userMapper;
    private final SysUserIdentityMapper userIdentityMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sync() {
        DirectoryProvider provider = providerRegistry.get(properties.getProvider());
        if (provider == null || !provider.isEnabled()) {
            throw new BusinessException("通讯录同步提供方未启用: " + properties.getProvider());
        }
        SysDept root = deptMapper.selectById(properties.getRootDeptId());
        if (root == null) {
            throw new BusinessException("通讯录同步根部门不存在: " + properties.getRootDeptId());
        }

        String providerName = provider.provider();
        String corpId = resolveCorpId(providerName);

        List<ExternalDepartment> departments = provider.fetchDepartments();
        List<ExternalMember> members = provider.fetchMembers();

        Map<String, Long> deptMapping = syncDepartments(providerName, corpId, departments);
        int createdUsers = 0;
        int updatedUsers = 0;
        for (ExternalMember member : members) {
            Long localDeptId = firstMappedDept(member.getDepartmentIds(), deptMapping);
            SysUserIdentity identity = findUserIdentity(providerName, corpId, member.getExternalUserId());
            if (identity != null) {
                if (updateUser(identity, member, localDeptId)) {
                    updatedUsers++;
                }
            } else if (properties.isCreateUsers()) {
                createUser(providerName, corpId, member, localDeptId);
                createdUsers++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", providerName);
        result.put("departments", departments.size());
        result.put("members", members.size());
        result.put("createdUsers", createdUsers);
        result.put("updatedUsers", updatedUsers);
        log.info("通讯录同步完成: {}", result);
        return result;
    }

    private Map<String, Long> syncDepartments(String provider, String corpId,
                                              List<ExternalDepartment> departments) {
        Map<String, ExternalDepartment> byExternalId = new HashMap<>();
        for (ExternalDepartment department : departments) {
            byExternalId.put(department.getExternalId(), department);
        }
        Map<String, Long> mapping = new HashMap<>();
        List<ExternalDepartment> remaining = new ArrayList<>(departments);
        for (int pass = 0; pass < MAX_DEPT_PASSES && !remaining.isEmpty(); pass++) {
            List<ExternalDepartment> deferred = new ArrayList<>();
            for (ExternalDepartment department : remaining) {
                Long parentLocalId = properties.getRootDeptId();
                if (StringUtils.hasText(department.getParentExternalId())) {
                    Long mapped = mapping.get(department.getParentExternalId());
                    if (mapped == null) {
                        // 父部门尚未创建，下一轮再处理
                        if (byExternalId.containsKey(department.getParentExternalId())) {
                            deferred.add(department);
                            continue;
                        }
                    } else {
                        parentLocalId = mapped;
                    }
                }
                mapping.put(department.getExternalId(),
                        upsertDepartment(provider, corpId, department, parentLocalId));
            }
            if (deferred.size() == remaining.size()) {
                // 防止脏数据导致死循环：剩余的挂到根节点
                for (ExternalDepartment department : deferred) {
                    mapping.put(department.getExternalId(),
                            upsertDepartment(provider, corpId, department, properties.getRootDeptId()));
                }
                remaining = new ArrayList<>();
            } else {
                remaining = deferred;
            }
        }
        return mapping;
    }

    private Long upsertDepartment(String provider, String corpId, ExternalDepartment external,
                                  Long parentLocalId) {
        SysDeptIdentity identity = deptIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysDeptIdentity>()
                        .eq(SysDeptIdentity::getProvider, provider)
                        .eq(SysDeptIdentity::getCorpId, corpId)
                        .eq(SysDeptIdentity::getExternalDeptId, external.getExternalId())
                        .last("limit 1"));
        if (identity != null) {
            SysDept dept = deptMapper.selectById(identity.getDeptId());
            if (dept != null) {
                SysDept update = new SysDept();
                update.setId(dept.getId());
                update.setParentId(parentLocalId);
                update.setDeptName(external.getName());
                update.setSort(external.getSort());
                update.setStatus(1);
                deptMapper.updateById(update);
                return dept.getId();
            }
        }

        SysDept dept = new SysDept();
        dept.setParentId(parentLocalId);
        dept.setDeptName(external.getName());
        dept.setType(3);
        dept.setSort(external.getSort());
        dept.setStatus(1);
        deptMapper.insert(dept);

        if (identity == null) {
            identity = new SysDeptIdentity();
            identity.setProvider(provider);
            identity.setCorpId(corpId);
            identity.setExternalDeptId(external.getExternalId());
        }
        identity.setDeptId(dept.getId());
        identity.setStatus(1);
        identity.setUpdateTime(LocalDateTime.now());
        if (identity.getId() == null) {
            identity.setCreateTime(LocalDateTime.now());
            deptIdentityMapper.insert(identity);
        } else {
            deptIdentityMapper.updateById(identity);
        }
        return dept.getId();
    }

    private boolean updateUser(SysUserIdentity identity, ExternalMember member, Long deptId) {
        SysUser user = userMapper.selectById(identity.getUserId());
        if (user == null) {
            log.warn("外部成员绑定的本地用户不存在，跳过: userId={}", identity.getUserId());
            return false;
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setNickname(StringUtils.hasText(member.getName()) ? member.getName() : user.getNickname());
        if (deptId != null) {
            update.setDeptId(deptId);
        }
        if (StringUtils.hasText(member.getMobile())) {
            update.setPhone(member.getMobile());
        }
        if (StringUtils.hasText(member.getEmail())) {
            update.setEmail(member.getEmail());
        }
        update.setStatus(member.isActive() ? 1 : 0);
        userMapper.updateById(update);

        identity.setUpdateTime(LocalDateTime.now());
        userIdentityMapper.updateById(identity);
        return true;
    }

    private void createUser(String provider, String corpId, ExternalMember member, Long deptId) {
        String username = buildUniqueUsername(member.getExternalUserId());
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(member.getName()) ? member.getName() : member.getExternalUserId());
        user.setDeptId(deptId == null ? properties.getRootDeptId() : deptId);
        user.setEmail(member.getEmail());
        user.setPhone(member.getMobile());
        user.setStatus(member.isActive() ? 1 : 0);
        user.setRemark("通讯录同步创建（" + provider + "）");
        userMapper.insert(user);
        assignDefaultRoles(user.getId());

        SysUserIdentity identity = new SysUserIdentity();
        identity.setUserId(user.getId());
        identity.setProvider(provider);
        identity.setCorpId(corpId);
        identity.setExternalUserId(member.getExternalUserId());
        identity.setStatus(1);
        identity.setBindTime(LocalDateTime.now());
        identity.setRemark("通讯录同步绑定");
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        userIdentityMapper.insert(identity);
        log.info("通讯录同步创建用户: provider={}, externalUserId={}, username={}",
                provider, member.getExternalUserId(), username);
    }

    private void assignDefaultRoles(Long userId) {
        String keys = properties.getDefaultRoleKeys();
        if (!StringUtils.hasText(keys)) {
            return;
        }
        for (String key : keys.split(",")) {
            String roleKey = key.trim();
            if (!StringUtils.hasText(roleKey)) {
                continue;
            }
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleKey, roleKey)
                    .eq(SysRole::getStatus, 1)
                    .last("limit 1"));
            if (role == null) {
                log.warn("通讯录同步默认角色不存在，已跳过: roleKey={}", roleKey);
                continue;
            }
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }
    }

    private SysUserIdentity findUserIdentity(String provider, String corpId, String externalUserId) {
        return userIdentityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, provider)
                .eq(SysUserIdentity::getCorpId, corpId)
                .eq(SysUserIdentity::getExternalUserId, externalUserId)
                .eq(SysUserIdentity::getStatus, 1)
                .last("limit 1"));
    }

    private Long firstMappedDept(List<String> externalDeptIds, Map<String, Long> mapping) {
        if (externalDeptIds != null) {
            for (String externalDeptId : externalDeptIds) {
                Long localId = mapping.get(externalDeptId);
                if (localId != null) {
                    return localId;
                }
            }
        }
        return properties.getRootDeptId();
    }

    private String resolveCorpId(String provider) {
        return WeComDirectoryProvider.PROVIDER.equalsIgnoreCase(provider)
                ? properties.getWecomCorpId() : properties.getCorpId();
    }

    private String buildUniqueUsername(String externalUserId) {
        String raw = externalUserId == null ? "user" : externalUserId;
        String base = properties.getUsernamePrefix() + raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.length() > MAX_USERNAME_LENGTH) {
            base = base.substring(0, MAX_USERNAME_LENGTH);
        }
        String candidate = base;
        int suffix = 2;
        while (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, candidate)) > 0) {
            candidate = base + "_" + suffix++;
            if (candidate.length() > MAX_USERNAME_LENGTH) {
                candidate = candidate.substring(0, MAX_USERNAME_LENGTH - 3) + "_" + suffix;
            }
            if (suffix > 100) {
                throw new IllegalStateException("无法生成唯一用户名: " + base);
            }
        }
        return candidate;
    }
}
