package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.config.DirectorySyncProperties;
import com.example.rbac.directory.DirectoryProvider;
import com.example.rbac.directory.DirectoryProviderRegistry;
import com.example.rbac.directory.ExternalDepartment;
import com.example.rbac.directory.ExternalMember;
import com.example.rbac.directory.MockDingTalkDirectoryProvider;
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
import com.example.rbac.mq.SsoKickPublisher;
import com.example.rbac.security.PermissionSnapshotStore;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部通讯录 / 部门同步（Push + Pull 混合）
 * <p>
 * Pull：按配置的 providers 拉取全量快照做对账；
 * Push：{@link DirectoryEventService} 调用本类的 upsert/deactivate 方法增量应用。
 * 两条路径共用同一套幂等写入逻辑，最终状态一致。
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
    private final PermissionSnapshotStore snapshotStore;
    private final SsoKickPublisher ssoKickPublisher;
    private final SysUserDeptService sysUserDeptService;

    /**
     * 拉取所有配置的提供方做对账
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sync() {
        SysDept root = deptMapper.selectById(properties.getRootDeptId());
        if (root == null) {
            throw new BusinessException("通讯录同步根部门不存在: " + properties.getRootDeptId());
        }
        if (properties.getProviders() == null || properties.getProviders().isEmpty()) {
            throw new BusinessException("未配置通讯录同步提供方");
        }

        List<Map<String, Object>> providerResults = new ArrayList<>();
        int totalDepartments = 0;
        int totalMembers = 0;
        int totalCreated = 0;
        int totalUpdated = 0;

        for (String name : properties.getProviders()) {
            DirectoryProvider provider = providerRegistry.get(name);
            if (provider == null || !provider.isEnabled()) {
                Map<String, Object> skipped = new LinkedHashMap<>();
                skipped.put("provider", name);
                skipped.put("enabled", false);
                providerResults.add(skipped);
                log.info("通讯录提供方未启用，跳过: {}", name);
                continue;
            }
            Map<String, Object> result = syncProvider(provider);
            providerResults.add(result);
            totalDepartments += (Integer) result.get("departments");
            totalMembers += (Integer) result.get("members");
            totalCreated += (Integer) result.get("createdUsers");
            totalUpdated += (Integer) result.get("updatedUsers");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providers", providerResults);
        result.put("departments", totalDepartments);
        result.put("members", totalMembers);
        result.put("createdUsers", totalCreated);
        result.put("updatedUsers", totalUpdated);
        log.info("通讯录同步完成: {}", result);
        return result;
    }

    private Map<String, Object> syncProvider(DirectoryProvider provider) {
        String providerName = provider.provider();
        String corpId = resolveCorpId(providerName);
        List<ExternalDepartment> departments = provider.fetchDepartments();
        List<ExternalMember> members = provider.fetchMembers();

        Map<String, Long> deptMapping = syncDepartments(providerName, corpId, departments);
        int createdUsers = 0;
        int updatedUsers = 0;
        for (ExternalMember member : members) {
            List<Long> localDeptIds = mappedDepts(member.getDepartmentIds(), deptMapping);
            Long localDeptId = mainDept(member, localDeptIds, deptMapping);
            SysUserIdentity identity = findUserIdentity(providerName, corpId, member.getExternalUserId());
            if (identity != null) {
                if (updateUser(identity, member, localDeptIds, localDeptId)) {
                    updatedUsers++;
                }
            } else if (properties.isCreateUsers()) {
                createUser(providerName, corpId, member, localDeptIds, localDeptId);
                createdUsers++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", providerName);
        result.put("enabled", true);
        result.put("departments", departments.size());
        result.put("members", members.size());
        result.put("createdUsers", createdUsers);
        result.put("updatedUsers", updatedUsers);
        return result;
    }

    /**
     * 推送事件：新增/更新一个部门
     */
    public Long upsertDepartment(String provider, String corpId, ExternalDepartment external,
                                 Long parentLocalId) {
        return doUpsertDepartment(provider, corpId, external, parentLocalId);
    }

    /**
     * 推送事件：新增/更新一个成员
     *
     * @return 本地用户ID；未建号且不允许自动建号时返回 null
     */
    public Long upsertMember(String provider, String corpId, ExternalMember member) {
        SysUserIdentity identity = findUserIdentity(provider, corpId, member.getExternalUserId());
        List<Long> deptIds = resolveMemberDepts(provider, corpId, member.getDepartmentIds());
        Long deptId = resolveMemberMainDept(provider, corpId, member, deptIds);
        if (identity != null) {
            updateUser(identity, member, deptIds, deptId);
            return identity.getUserId();
        }
        if (!properties.isCreateUsers()) {
            log.info("成员未绑定且未开启自动建号，跳过: provider={}, externalUserId={}",
                    provider, member.getExternalUserId());
            return null;
        }
        return createUser(provider, corpId, member, deptIds, deptId);
    }

    /**
     * 推送事件：停用成员（不物理删除，保留历史与审计），并踢下线
     */
    public boolean deactivateUser(String provider, String corpId, String externalUserId) {
        SysUserIdentity identity = findUserIdentity(provider, corpId, externalUserId);
        if (identity == null) {
            return false;
        }
        SysUser user = userMapper.selectById(identity.getUserId());
        if (user == null) {
            return false;
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setStatus(0);
        userMapper.updateById(update);

        identity.setStatus(0);
        identity.setUpdateTime(LocalDateTime.now());
        userIdentityMapper.updateById(identity);

        snapshotStore.invalidateUser(user.getId());
        ssoKickPublisher.publish(SsoKickPublisher.TYPE_DISABLED, null, user.getId(),
                user.getUsername(), "通讯录同步：成员已删除/停用");
        log.info("通讯录同步停用用户: provider={}, username={}", provider, user.getUsername());
        return true;
    }

    /**
     * 推送事件：停用部门（保留本地组织节点，仅置为禁用）
     */
    public boolean deactivateDepartment(String provider, String corpId, String externalDeptId) {
        SysDeptIdentity identity = findDeptIdentity(provider, corpId, externalDeptId);
        if (identity == null) {
            return false;
        }
        SysDept dept = deptMapper.selectById(identity.getDeptId());
        if (dept != null) {
            SysDept update = new SysDept();
            update.setId(dept.getId());
            update.setStatus(0);
            deptMapper.updateById(update);
        }
        identity.setStatus(0);
        identity.setUpdateTime(LocalDateTime.now());
        deptIdentityMapper.updateById(identity);
        return true;
    }

    public Long findDeptLocalId(String provider, String corpId, String externalDeptId) {
        SysDeptIdentity identity = findDeptIdentity(provider, corpId, externalDeptId);
        return identity == null ? null : identity.getDeptId();
    }

    public String resolveCorpId(String provider) {
        if (WeComDirectoryProvider.PROVIDER.equalsIgnoreCase(provider)) {
            return properties.getWecomCorpId();
        }
        if (MockDingTalkDirectoryProvider.PROVIDER.equalsIgnoreCase(provider)) {
            return properties.getDingtalkCorpId();
        }
        return properties.getCorpId();
    }

    // ==================== 内部实现 ====================

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
                        if (byExternalId.containsKey(department.getParentExternalId())) {
                            deferred.add(department);
                            continue;
                        }
                    } else {
                        parentLocalId = mapped;
                    }
                }
                mapping.put(department.getExternalId(),
                        doUpsertDepartment(provider, corpId, department, parentLocalId));
            }
            if (deferred.size() == remaining.size()) {
                for (ExternalDepartment department : deferred) {
                    mapping.put(department.getExternalId(),
                            doUpsertDepartment(provider, corpId, department, properties.getRootDeptId()));
                }
                remaining = new ArrayList<>();
            } else {
                remaining = deferred;
            }
        }
        return mapping;
    }

    private Long doUpsertDepartment(String provider, String corpId, ExternalDepartment external,
                                    Long parentLocalId) {
        SysDeptIdentity identity = findDeptIdentity(provider, corpId, external.getExternalId());
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

    private boolean updateUser(SysUserIdentity identity, ExternalMember member,
                               List<Long> deptIds, Long deptId) {
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
        applyMemberProfile(update, member);
        update.setStatus(member.isActive() ? 1 : 0);
        userMapper.updateById(update);
        sysUserDeptService.replace(user.getId(), deptId, deptIds,
                member.isLeaderInDept(), member.getOrderInDept());

        identity.setUpdateTime(LocalDateTime.now());
        userIdentityMapper.updateById(identity);
        return true;
    }

    private Long createUser(String provider, String corpId, ExternalMember member,
                            List<Long> deptIds, Long deptId) {
        String username = buildUniqueUsername(member.getExternalUserId());
        Long mainDeptId = deptId == null ? properties.getRootDeptId() : deptId;
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(StringUtils.hasText(member.getName()) ? member.getName() : member.getExternalUserId());
        user.setDeptId(mainDeptId);
        user.setEmail(member.getEmail());
        user.setPhone(member.getMobile());
        applyMemberProfile(user, member);
        user.setStatus(member.isActive() ? 1 : 0);
        user.setRemark("通讯录同步创建（" + provider + "）");
        userMapper.insert(user);
        assignDefaultRoles(user.getId());
        sysUserDeptService.replace(user.getId(), mainDeptId, deptIds,
                member.isLeaderInDept(), member.getOrderInDept());

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
        return user.getId();
    }

    /** 外部成员资料字段，空值不覆盖本地已有值 */
    private void applyMemberProfile(SysUser user, ExternalMember member) {
        if (StringUtils.hasText(member.getEmployeeNo())) {
            user.setEmployeeNo(member.getEmployeeNo());
        }
        if (StringUtils.hasText(member.getAlias())) {
            user.setAlias(member.getAlias());
        }
        if (StringUtils.hasText(member.getPosition())) {
            user.setPosition(member.getPosition());
        }
        if (member.getGender() != null) {
            user.setGender(member.getGender());
        }
        if (StringUtils.hasText(member.getBizMail())) {
            user.setBizMail(member.getBizMail());
        }
        if (StringUtils.hasText(member.getTelephone())) {
            user.setTelephone(member.getTelephone());
        }
        if (StringUtils.hasText(member.getDirectLeader())) {
            user.setDirectLeader(member.getDirectLeader());
        }
        if (StringUtils.hasText(member.getExternalPosition())) {
            user.setExternalPosition(member.getExternalPosition());
        }
        if (StringUtils.hasText(member.getExternalNickname())) {
            user.setExternalNickname(member.getExternalNickname());
        }
        if (StringUtils.hasText(member.getAddress())) {
            user.setAddress(member.getAddress());
        }
        if (StringUtils.hasText(member.getExtAttr())) {
            user.setExtAttr(member.getExtAttr());
        }
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

    private SysDeptIdentity findDeptIdentity(String provider, String corpId, String externalDeptId) {
        return deptIdentityMapper.selectOne(new LambdaQueryWrapper<SysDeptIdentity>()
                .eq(SysDeptIdentity::getProvider, provider)
                .eq(SysDeptIdentity::getCorpId, corpId)
                .eq(SysDeptIdentity::getExternalDeptId, externalDeptId)
                .last("limit 1"));
    }

    private List<Long> mappedDepts(List<String> externalDeptIds, Map<String, Long> mapping) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (externalDeptIds != null) {
            for (String externalDeptId : externalDeptIds) {
                Long localId = mapping.get(externalDeptId);
                if (localId != null) {
                    result.add(localId);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(properties.getRootDeptId());
        }
        return new ArrayList<>(result);
    }

    private Long mainDept(ExternalMember member, List<Long> localDeptIds, Map<String, Long> mapping) {
        if (member.getMainDepartmentId() != null) {
            Long localId = mapping.get(member.getMainDepartmentId());
            if (localId != null) {
                return localId;
            }
        }
        return localDeptIds.isEmpty() ? properties.getRootDeptId() : localDeptIds.get(0);
    }

    private List<Long> resolveMemberDepts(String provider, String corpId, List<String> externalDeptIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (externalDeptIds != null) {
            for (String externalDeptId : externalDeptIds) {
                Long localId = findDeptLocalId(provider, corpId, externalDeptId);
                if (localId != null) {
                    result.add(localId);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(properties.getRootDeptId());
        }
        return new ArrayList<>(result);
    }

    private Long resolveMemberMainDept(String provider, String corpId, ExternalMember member,
                                       List<Long> localDeptIds) {
        if (member.getMainDepartmentId() != null) {
            Long localId = findDeptLocalId(provider, corpId, member.getMainDepartmentId());
            if (localId != null) {
                return localId;
            }
        }
        return localDeptIds.isEmpty() ? properties.getRootDeptId() : localDeptIds.get(0);
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
