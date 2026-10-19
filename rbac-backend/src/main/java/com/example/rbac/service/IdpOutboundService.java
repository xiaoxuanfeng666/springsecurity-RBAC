package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.config.IdpOutboundProperties;
import com.example.rbac.entity.SysDept;
import com.example.rbac.entity.SysDeptIdentity;
import com.example.rbac.entity.SysIdpOutbox;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserDept;
import com.example.rbac.entity.SysUserIdentity;
import com.example.rbac.mapper.SysDeptIdentityMapper;
import com.example.rbac.mapper.SysDeptMapper;
import com.example.rbac.mapper.SysIdpOutboxMapper;
import com.example.rbac.mapper.SysUserDeptMapper;
import com.example.rbac.mapper.SysUserIdentityMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.outbound.OutboundDepartment;
import com.example.rbac.outbound.OutboundDirectoryProvider;
import com.example.rbac.outbound.OutboundProviderRegistry;
import com.example.rbac.outbound.OutboundUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RBAC → 外部通讯录的 Outbox 入队
 * <p>
 * 本地用户/部门变更时在同一个事务里写入 outbox；后台任务异步调用外部 API。
 * 管理端还可以手动指定提供方，单独触发某个用户/部门的同步。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdpOutboundService {

    public static final String OP_USER_CREATE = "USER_CREATE";
    public static final String OP_USER_UPDATE = "USER_UPDATE";
    public static final String OP_USER_DELETE = "USER_DELETE";
    public static final String OP_DEPT_CREATE = "DEPT_CREATE";
    public static final String OP_DEPT_UPDATE = "DEPT_UPDATE";
    public static final String OP_DEPT_DELETE = "DEPT_DELETE";

    /** 企业微信/钉钉合法的 userid：数字、字母、下划线、短横线，首字符为数字或字母 */
    private static final Pattern VALID_USER_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private final IdpOutboundProperties properties;
    private final OutboundProviderRegistry providerRegistry;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserIdentityMapper userIdentityMapper;
    private final SysUserDeptMapper userDeptMapper;
    private final SysDeptIdentityMapper deptIdentityMapper;
    private final SysIdpOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 管理端可选的出站提供方
     */
    public List<Map<String, String>> enabledProviders() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String name : properties.getProviders()) {
            OutboundDirectoryProvider provider = providerRegistry.get(name);
            if (provider == null || !provider.isEnabled()) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", provider.provider());
            item.put("label", label(provider.provider()));
            result.add(item);
        }
        return result;
    }

    /** 本地用户新增/修改/删除后自动入队（受 idp.outbound.enabled 控制） */
    public void autoEnqueueUser(String action, SysUser user) {
        if (!properties.isEnabled() || user == null) {
            return;
        }
        for (Map<String, String> provider : enabledProviders()) {
            enqueueUser(provider.get("code"), user, action);
        }
    }

    /** 本地部门新增/修改/删除后自动入队（受 idp.outbound.enabled 控制） */
    public void autoEnqueueDept(String action, SysDept dept) {
        if (!properties.isEnabled() || dept == null) {
            return;
        }
        for (Map<String, String> provider : enabledProviders()) {
            enqueueDept(provider.get("code"), dept, action);
        }
    }

    /**
     * 管理端手动同步一个用户：创建或更新（根据是否已有绑定自动判断）
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualSyncUser(Long userId, String provider) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        requireProvider(provider);
        enqueueUser(provider, user, null);
    }

    /**
     * 管理端手动同步一个部门
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualSyncDept(Long deptId, String provider) {
        SysDept dept = deptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }
        requireProvider(provider);
        enqueueDept(provider, dept, null);
    }

    /**
     * 全量推送：先按部门树顺序入队部门，再入队全部用户。
     * 根部门映射到外部根部门 1，不重复创建。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pushAll(String provider) {
        requireProvider(provider);
        String corpId = resolveCorpId(provider);
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("提供方未配置企业ID: " + provider);
        }
        ensureRootMapping(provider, corpId);

        List<SysDept> departments = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getParentId, SysDept::getId));
        int deptCount = 0;
        for (SysDept dept : orderDepartments(departments)) {
            if (isRootDept(dept)) {
                continue;
            }
            enqueueDept(provider, dept, null);
            deptCount++;
        }

        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>());
        int userCount = 0;
        for (SysUser user : users) {
            enqueueUser(provider, user, null);
            userCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider);
        result.put("departments", deptCount);
        result.put("users", userCount);
        log.info("全量推送已入队: provider={}, departments={}, users={}", provider, deptCount, userCount);
        return result;
    }

    /** 按父子关系做 BFS，保证父部门先于子部门入队 */
    private List<SysDept> orderDepartments(List<SysDept> departments) {
        Map<Long, List<SysDept>> byParent = new HashMap<>();
        for (SysDept dept : departments) {
            long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dept);
        }
        List<SysDept> ordered = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(0L);
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            for (SysDept child : byParent.getOrDefault(parentId, new ArrayList<>())) {
                if (seen.add(child.getId())) {
                    ordered.add(child);
                    queue.add(child.getId());
                }
            }
        }
        for (SysDept dept : departments) {
            if (seen.add(dept.getId())) {
                ordered.add(dept);
            }
        }
        return ordered;
    }

    private void ensureRootMapping(String provider, String corpId) {
        if (!"wecom".equalsIgnoreCase(provider) && !"dingtalk".equalsIgnoreCase(provider)) {
            return;
        }
        Long rootId = properties.getRootDeptId();
        SysDeptIdentity existing = findDeptIdentityByDeptId(provider, corpId, rootId);
        if (existing != null) {
            if (!Integer.valueOf(1).equals(existing.getStatus())) {
                existing.setStatus(1);
                existing.setUpdateTime(LocalDateTime.now());
                deptIdentityMapper.updateById(existing);
            }
            return;
        }
        SysDeptIdentity identity = new SysDeptIdentity();
        identity.setProvider(provider);
        identity.setCorpId(corpId);
        identity.setExternalDeptId("1");
        identity.setDeptId(rootId);
        identity.setStatus(1);
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        deptIdentityMapper.insert(identity);
    }

    private boolean isRootDept(SysDept dept) {
        return dept.getParentId() == null || dept.getParentId() == 0L
                || dept.getId().equals(properties.getRootDeptId());
    }

    private String resolveExternalUserId(SysUser user, SysUserIdentity identity) {
        if (identity != null) {
            return identity.getExternalUserId();
        }
        if (StringUtils.hasText(user.getEmployeeNo())
                && VALID_USER_ID.matcher(user.getEmployeeNo()).matches()) {
            return user.getEmployeeNo();
        }
        if (StringUtils.hasText(user.getUsername())
                && VALID_USER_ID.matcher(user.getUsername()).matches()) {
            return user.getUsername();
        }
        return "u" + user.getId();
    }

    private void requireProvider(String provider) {
        OutboundDirectoryProvider p = providerRegistry.get(provider);
        if (p == null || !p.isEnabled()) {
            throw new BusinessException("外部同步提供方未启用: " + provider);
        }
    }

    private void enqueueUser(String provider, SysUser user, String action) {
        String corpId = resolveCorpId(provider);
        if (!StringUtils.hasText(corpId)) {
            log.warn("提供方未配置企业ID，跳过出站同步: provider={}", provider);
            return;
        }
        SysUserIdentity identity = findUserIdentity(provider, corpId, user.getId());
        boolean deleting = "DELETE".equalsIgnoreCase(action);
        if (deleting && identity == null) {
            return;
        }
        String externalUserId = resolveExternalUserId(user, identity);
        String operation;
        if (deleting) {
            operation = OP_USER_DELETE;
        } else if (identity != null) {
            operation = OP_USER_UPDATE;
        } else {
            operation = OP_USER_CREATE;
        }
        // 创建后外部身份尚未回写时又改了资料：复用待处理的 CREATE，避免重复建号
        if (!deleting && identity == null
                && hasPending(provider, SysIdpOutbox.TARGET_USER, user.getId(), OP_USER_CREATE)) {
            operation = OP_USER_UPDATE;
        }

        OutboundUser payload = new OutboundUser();
        payload.setUserId(user.getId());
        payload.setExternalUserId(externalUserId);
        payload.setUsername(user.getUsername());
        payload.setName(user.getNickname());
        payload.setEmployeeNo(user.getEmployeeNo());
        payload.setAlias(user.getAlias());
        payload.setPosition(user.getPosition());
        payload.setGender(user.getGender());
        payload.setMobile(user.getPhone());
        payload.setEmail(user.getEmail());
        payload.setBizMail(user.getBizMail());
        payload.setTelephone(user.getTelephone());
        payload.setDirectLeader(resolveDirectLeader(provider, corpId, user.getDirectLeader()));
        payload.setExternalPosition(user.getExternalPosition());
        payload.setExternalNickname(user.getExternalNickname());
        payload.setAddress(user.getAddress());
        payload.setExtAttr(user.getExtAttr());

        List<SysUserDept> memberships = userDeptMapper.selectList(
                new LambdaQueryWrapper<SysUserDept>()
                        .eq(SysUserDept::getUserId, user.getId())
                        .orderByDesc(SysUserDept::getIsMain)
                        .orderByAsc(SysUserDept::getId));
        List<Long> localDeptIds = new ArrayList<>();
        for (SysUserDept membership : memberships) {
            localDeptIds.add(membership.getDeptId());
        }
        if (localDeptIds.isEmpty() && user.getDeptId() != null) {
            localDeptIds.add(user.getDeptId());
        }
        SysUserDept main = memberships.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getIsMain()))
                .findFirst().orElse(memberships.isEmpty() ? null : memberships.get(0));
        payload.setLeaderInDept(main != null && Integer.valueOf(1).equals(main.getIsLeader()));
        payload.setOrderInDept(main == null || main.getOrderInDept() == null ? 0 : main.getOrderInDept());
        payload.setDepartmentIds(resolveDeptExternalIds(provider, corpId, localDeptIds));
        payload.setLocalDeptIds(localDeptIds);
        payload.setEnabled(Integer.valueOf(1).equals(user.getStatus()));
        enqueue(provider, operation, SysIdpOutbox.TARGET_USER, user.getId(), payload);
    }

    private void enqueueDept(String provider, SysDept dept, String action) {
        String corpId = resolveCorpId(provider);
        if (!StringUtils.hasText(corpId)) {
            log.warn("提供方未配置企业ID，跳过出站同步: provider={}", provider);
            return;
        }
        if (isRootDept(dept)
                && ("wecom".equalsIgnoreCase(provider) || "dingtalk".equalsIgnoreCase(provider))) {
            ensureRootMapping(provider, corpId);
            return;
        }
        SysDeptIdentity identity = findDeptIdentityByDeptId(provider, corpId, dept.getId());
        boolean deleting = "DELETE".equalsIgnoreCase(action);
        if (deleting && identity == null) {
            return;
        }
        String operation;
        if (deleting) {
            operation = OP_DEPT_DELETE;
        } else if (identity != null) {
            operation = OP_DEPT_UPDATE;
        } else {
            operation = OP_DEPT_CREATE;
        }
        if (!deleting && identity == null
                && hasPending(provider, SysIdpOutbox.TARGET_DEPT, dept.getId(), OP_DEPT_CREATE)) {
            operation = OP_DEPT_UPDATE;
        }

        OutboundDepartment payload = new OutboundDepartment();
        payload.setDeptId(dept.getId());
        payload.setExternalDeptId(identity == null ? null : identity.getExternalDeptId());
        payload.setName(dept.getDeptName());
        payload.setSort(dept.getSort() == null ? 0 : dept.getSort());
        if (dept.getParentId() != null && dept.getParentId() != 0L) {
            payload.setParentDeptId(dept.getParentId());
            SysDeptIdentity parent = findDeptIdentityByDeptId(provider, corpId, dept.getParentId());
            if (parent != null) {
                payload.setParentExternalDeptId(parent.getExternalDeptId());
            }
        }
        enqueue(provider, operation, SysIdpOutbox.TARGET_DEPT, dept.getId(), payload);
    }

    private void enqueue(String provider, String operation, String targetType,
                         Long targetId, Object payload) {
        SysIdpOutbox row = new SysIdpOutbox();
        row.setProvider(provider);
        row.setOperation(operation);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setStatus(SysIdpOutbox.STATUS_PENDING);
        row.setRetryCount(0);
        row.setNextRetryTime(LocalDateTime.now());
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        try {
            row.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException("组装外部同步消息失败: " + e.getMessage());
        }
        outboxMapper.insert(row);
        log.info("已加入外部同步队列: provider={}, operation={}, target={}#{}",
                provider, operation, targetType, targetId);
    }

    /** 直属上级若填的是本地用户名，转换成该提供方下的外部 userid */
    private String resolveDirectLeader(String provider, String corpId, String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String token = part.trim();
            if (!StringUtils.hasText(token)) {
                continue;
            }
            SysUser leader = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, token)
                    .last("limit 1"));
            SysUserIdentity identity = leader == null
                    ? null : findUserIdentity(provider, corpId, leader.getId());
            result.add(identity == null ? token : identity.getExternalUserId());
        }
        return String.join(",", result);
    }

    private boolean hasPending(String provider, String targetType, Long targetId, String operation) {
        Long count = outboxMapper.selectCount(new LambdaQueryWrapper<SysIdpOutbox>()
                .eq(SysIdpOutbox::getProvider, provider)
                .eq(SysIdpOutbox::getTargetType, targetType)
                .eq(SysIdpOutbox::getTargetId, targetId)
                .eq(SysIdpOutbox::getOperation, operation)
                .eq(SysIdpOutbox::getStatus, SysIdpOutbox.STATUS_PENDING));
        return count != null && count > 0;
    }

    private List<String> resolveDeptExternalIds(String provider, String corpId, List<Long> deptIds) {
        List<String> result = new ArrayList<>();
        if (deptIds == null) {
            return result;
        }
        for (Long deptId : deptIds) {
            if (deptId == null || deptId == 0L) {
                continue;
            }
            SysDeptIdentity identity = findDeptIdentityByDeptId(provider, corpId, deptId);
            if (identity != null && !result.contains(identity.getExternalDeptId())) {
                result.add(identity.getExternalDeptId());
            }
        }
        return result;
    }

    private SysUserIdentity findUserIdentity(String provider, String corpId, Long userId) {
        return userIdentityMapper.selectOne(new LambdaQueryWrapper<SysUserIdentity>()
                .eq(SysUserIdentity::getProvider, provider)
                .eq(SysUserIdentity::getCorpId, corpId)
                .eq(SysUserIdentity::getUserId, userId)
                .eq(SysUserIdentity::getStatus, 1)
                .last("limit 1"));
    }

    private SysDeptIdentity findDeptIdentityByDeptId(String provider, String corpId, Long deptId) {
        return deptIdentityMapper.selectOne(new LambdaQueryWrapper<SysDeptIdentity>()
                .eq(SysDeptIdentity::getProvider, provider)
                .eq(SysDeptIdentity::getCorpId, corpId)
                .eq(SysDeptIdentity::getDeptId, deptId)
                .eq(SysDeptIdentity::getStatus, 1)
                .last("limit 1"));
    }

    public String resolveCorpId(String provider) {
        if ("wecom".equalsIgnoreCase(provider)) {
            return properties.getWecomCorpId();
        }
        if ("dingtalk".equalsIgnoreCase(provider)) {
            return properties.getDingtalkCorpId();
        }
        return properties.getCorpId();
    }

    private String label(String provider) {
        if ("wecom".equalsIgnoreCase(provider)) {
            return "企业微信";
        }
        if ("dingtalk".equalsIgnoreCase(provider)) {
            return "钉钉";
        }
        return "Mock（测试）";
    }
}
