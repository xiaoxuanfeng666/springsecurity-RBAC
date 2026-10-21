package com.example.rbac.outbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.config.IdpOutboundProperties;
import com.example.rbac.entity.SysDeptIdentity;
import com.example.rbac.entity.SysIdpOutbox;
import com.example.rbac.entity.SysUserIdentity;
import com.example.rbac.mapper.SysDeptIdentityMapper;
import com.example.rbac.mapper.SysIdpOutboxMapper;
import com.example.rbac.mapper.SysUserIdentityMapper;
import com.example.rbac.service.IdpOutboundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox 后台处理器：调用外部通讯录 API，失败按指数退避重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdpOutboxProcessor {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_BACKOFF_SECONDS = 300;

    private final IdpOutboundProperties properties;
    private final OutboundProviderRegistry providerRegistry;
    private final SysIdpOutboxMapper outboxMapper;
    private final SysUserIdentityMapper userIdentityMapper;
    private final SysDeptIdentityMapper deptIdentityMapper;
    private final IdpOutboundService outboundService;
    private final ObjectMapper objectMapper;

    /**
     * 处理一批待发送的记录（由 {@code IdpOutboxProcessTask} 定时驱动）
     */
    public void processPending() {
        LocalDateTime now = LocalDateTime.now();
        List<SysIdpOutbox> rows = outboxMapper.selectList(
                new LambdaQueryWrapper<SysIdpOutbox>()
                        .eq(SysIdpOutbox::getStatus, SysIdpOutbox.STATUS_PENDING)
                        .and(w -> w.isNull(SysIdpOutbox::getNextRetryTime)
                                .or().le(SysIdpOutbox::getNextRetryTime, now))
                        .orderByAsc(SysIdpOutbox::getId)
                        .last("limit " + BATCH_SIZE));
        for (SysIdpOutbox row : rows) {
            processRow(row);
        }
    }

    private void processRow(SysIdpOutbox row) {
        OutboundDirectoryProvider provider = providerRegistry.get(row.getProvider());
        if (provider == null || !provider.isEnabled()) {
            retryLater(row, "外部提供方未启用: " + row.getProvider());
            return;
        }
        try {
            switch (row.getOperation()) {
                case IdpOutboundService.OP_USER_CREATE: {
                    OutboundUser user = objectMapper.readValue(row.getPayload(), OutboundUser.class);
                    resolveUserDepartments(row.getProvider(), user);
                    String externalUserId = provider.createUser(user);
                    bindUserIdentity(row.getProvider(), user.getUserId(),
                            externalUserId == null ? user.getExternalUserId() : externalUserId);
                    break;
                }
                case IdpOutboundService.OP_USER_UPDATE: {
                    OutboundUser user = objectMapper.readValue(row.getPayload(), OutboundUser.class);
                    resolveUserDepartments(row.getProvider(), user);
                    provider.updateUser(user);
                    bindUserIdentity(row.getProvider(), user.getUserId(), user.getExternalUserId());
                    break;
                }
                case IdpOutboundService.OP_USER_DELETE: {
                    OutboundUser user = objectMapper.readValue(row.getPayload(), OutboundUser.class);
                    provider.deleteUser(user.getExternalUserId());
                    deactivateUserIdentity(row.getProvider(), user.getExternalUserId());
                    break;
                }
                case IdpOutboundService.OP_DEPT_CREATE: {
                    OutboundDepartment dept = objectMapper.readValue(row.getPayload(), OutboundDepartment.class);
                    resolveParentDept(row.getProvider(), dept);
                    String externalDeptId = provider.createDepartment(dept);
                    if (externalDeptId == null || externalDeptId.isEmpty()) {
                        throw new IllegalStateException("外部系统未返回部门ID");
                    }
                    bindDeptIdentity(row.getProvider(), dept.getDeptId(), externalDeptId);
                    break;
                }
                case IdpOutboundService.OP_DEPT_UPDATE: {
                    OutboundDepartment dept = objectMapper.readValue(row.getPayload(), OutboundDepartment.class);
                    resolveParentDept(row.getProvider(), dept);
                    provider.updateDepartment(dept);
                    break;
                }
                case IdpOutboundService.OP_DEPT_DELETE: {
                    OutboundDepartment dept = objectMapper.readValue(row.getPayload(), OutboundDepartment.class);
                    provider.deleteDepartment(dept.getExternalDeptId());
                    deactivateDeptIdentity(row.getProvider(), dept.getExternalDeptId());
                    break;
                }
                default:
                    throw new IllegalStateException("未知操作: " + row.getOperation());
            }
            row.setStatus(SysIdpOutbox.STATUS_SUCCESS);
            row.setLastError(null);
            row.setUpdateTime(LocalDateTime.now());
            outboxMapper.updateById(row);
            log.info("外部同步成功: provider={}, operation={}, target={}#{}",
                    row.getProvider(), row.getOperation(), row.getTargetType(), row.getTargetId());
        } catch (Exception e) {
            retryLater(row, e.getMessage());
        }
    }

    private void retryLater(SysIdpOutbox row, String error) {        int retry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        row.setRetryCount(retry);
        row.setLastError(truncate(error, 1000));
        row.setUpdateTime(LocalDateTime.now());
        if (retry >= properties.getMaxRetries()) {
            row.setStatus(SysIdpOutbox.STATUS_FAILED);
            log.error("外部同步失败且超过重试次数: provider={}, operation={}, target={}#{}, error={}",
                    row.getProvider(), row.getOperation(), row.getTargetType(), row.getTargetId(), error);
        } else {
            row.setStatus(SysIdpOutbox.STATUS_PENDING);
            row.setNextRetryTime(LocalDateTime.now()
                    .plusSeconds(Math.min(MAX_BACKOFF_SECONDS, 10L * retry)));
            log.warn("外部同步失败，稍后重试({}/{}): provider={}, operation={}, error={}",
                    retry, properties.getMaxRetries(), row.getProvider(), row.getOperation(), error);
        }
        outboxMapper.updateById(row);
    }

    /** 用户入队时可能还没拿到外部部门ID，处理时再按本地部门映射解析 */
    private void resolveUserDepartments(String provider, OutboundUser user) {
        if (user.getLocalDeptIds() == null || user.getLocalDeptIds().isEmpty()) {
            return;
        }
        String corpId = outboundService.resolveCorpId(provider);
        List<String> externalIds = new ArrayList<>();
        for (Long deptId : user.getLocalDeptIds()) {
            if (deptId == null) {
                continue;
            }
            SysDeptIdentity identity = deptIdentityMapper.selectOne(
                    new LambdaQueryWrapper<SysDeptIdentity>()
                            .eq(SysDeptIdentity::getProvider, provider)
                            .eq(SysDeptIdentity::getCorpId, corpId)
                            .eq(SysDeptIdentity::getDeptId, deptId)
                            .eq(SysDeptIdentity::getStatus, 1)
                            .last("limit 1"));
            if (identity != null && !externalIds.contains(identity.getExternalDeptId())) {
                externalIds.add(identity.getExternalDeptId());
            }
        }
        if (!externalIds.isEmpty()) {
            user.setDepartmentIds(externalIds);
        }
    }

    /** 父部门可能在后一条 outbox 才创建，处理时再解析父部门外部ID */
    private void resolveParentDept(String provider, OutboundDepartment dept) {
        if (StringUtils.hasText(dept.getParentExternalDeptId()) || dept.getParentDeptId() == null) {
            return;
        }
        String corpId = outboundService.resolveCorpId(provider);
        SysDeptIdentity identity = deptIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysDeptIdentity>()
                        .eq(SysDeptIdentity::getProvider, provider)
                        .eq(SysDeptIdentity::getCorpId, corpId)
                        .eq(SysDeptIdentity::getDeptId, dept.getParentDeptId())
                        .eq(SysDeptIdentity::getStatus, 1)
                        .last("limit 1"));
        if (identity != null) {
            dept.setParentExternalDeptId(identity.getExternalDeptId());
        }
    }

    private void bindUserIdentity(String provider, Long userId, String externalUserId) {
        String corpId = outboundService.resolveCorpId(provider);
        SysUserIdentity existing = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysUserIdentity>()
                        .eq(SysUserIdentity::getProvider, provider)
                        .eq(SysUserIdentity::getCorpId, corpId)
                        .eq(SysUserIdentity::getExternalUserId, externalUserId)
                        .last("limit 1"));
        if (existing != null) {
            boolean changed = false;
            if (!userId.equals(existing.getUserId())) {
                existing.setUserId(userId);
                changed = true;
            }
            if (!Integer.valueOf(1).equals(existing.getStatus())) {
                existing.setStatus(1);
                changed = true;
            }
            if (changed) {
                existing.setUpdateTime(LocalDateTime.now());
                userIdentityMapper.updateById(existing);
            }
            return;
        }
        SysUserIdentity identity = new SysUserIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setCorpId(corpId);
        identity.setExternalUserId(externalUserId);
        identity.setStatus(1);
        identity.setBindTime(LocalDateTime.now());
        identity.setRemark("RBAC 出站同步创建");
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        userIdentityMapper.insert(identity);
    }

    private void deactivateUserIdentity(String provider, String externalUserId) {
        String corpId = outboundService.resolveCorpId(provider);
        SysUserIdentity identity = userIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysUserIdentity>()
                        .eq(SysUserIdentity::getProvider, provider)
                        .eq(SysUserIdentity::getCorpId, corpId)
                        .eq(SysUserIdentity::getExternalUserId, externalUserId)
                        .last("limit 1"));
        if (identity != null) {
            identity.setStatus(0);
            identity.setUpdateTime(LocalDateTime.now());
            userIdentityMapper.updateById(identity);
        }
    }

    private void bindDeptIdentity(String provider, Long deptId, String externalDeptId) {
        String corpId = outboundService.resolveCorpId(provider);
        SysDeptIdentity existing = deptIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysDeptIdentity>()
                        .eq(SysDeptIdentity::getProvider, provider)
                        .eq(SysDeptIdentity::getCorpId, corpId)
                        .eq(SysDeptIdentity::getExternalDeptId, externalDeptId)
                        .last("limit 1"));
        if (existing != null) {
            boolean changed = false;
            if (!deptId.equals(existing.getDeptId())) {
                existing.setDeptId(deptId);
                changed = true;
            }
            if (!Integer.valueOf(1).equals(existing.getStatus())) {
                existing.setStatus(1);
                changed = true;
            }
            if (changed) {
                existing.setUpdateTime(LocalDateTime.now());
                deptIdentityMapper.updateById(existing);
            }
            return;
        }
        SysDeptIdentity identity = new SysDeptIdentity();
        identity.setProvider(provider);
        identity.setCorpId(corpId);
        identity.setExternalDeptId(externalDeptId);
        identity.setDeptId(deptId);
        identity.setStatus(1);
        identity.setCreateTime(LocalDateTime.now());
        identity.setUpdateTime(LocalDateTime.now());
        deptIdentityMapper.insert(identity);
    }

    private void deactivateDeptIdentity(String provider, String externalDeptId) {
        String corpId = outboundService.resolveCorpId(provider);
        SysDeptIdentity identity = deptIdentityMapper.selectOne(
                new LambdaQueryWrapper<SysDeptIdentity>()
                        .eq(SysDeptIdentity::getProvider, provider)
                        .eq(SysDeptIdentity::getCorpId, corpId)
                        .eq(SysDeptIdentity::getExternalDeptId, externalDeptId)
                        .last("limit 1"));
        if (identity != null) {
            identity.setStatus(0);
            identity.setUpdateTime(LocalDateTime.now());
            deptIdentityMapper.updateById(identity);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
