package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.common.BusinessException;
import com.example.rbac.config.DirectorySyncProperties;
import com.example.rbac.directory.DirectoryEvent;
import com.example.rbac.directory.DirectoryProvider;
import com.example.rbac.directory.DirectoryProviderRegistry;
import com.example.rbac.directory.ExternalDepartment;
import com.example.rbac.directory.ExternalMember;
import com.example.rbac.entity.SysDirectoryEvent;
import com.example.rbac.mapper.SysDirectoryEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 外部通讯录推送事件处理
 * <p>
 * Push 与 Pull 共用 {@link DirectorySyncService} 的幂等写入；
 * provider + event_id 唯一，重复投递直接返回 duplicate。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryEventService {

    private final DirectorySyncProperties properties;
    private final DirectoryProviderRegistry providerRegistry;
    private final SysDirectoryEventMapper eventMapper;
    private final DirectorySyncService syncService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> handle(String provider, DirectoryEvent event) {
        DirectoryProvider directoryProvider = providerRegistry.get(provider);
        if (directoryProvider == null || !directoryProvider.isEnabled()) {
            throw new BusinessException("外部来源未启用: " + provider);
        }
        if (!StringUtils.hasText(event.getEventType())) {
            throw new BusinessException("eventType 不能为空");
        }
        String eventId = StringUtils.hasText(event.getEventId())
                ? event.getEventId()
                : provider + "-" + UUID.randomUUID().toString().replace("-", "");

        Long exists = eventMapper.selectCount(new LambdaQueryWrapper<SysDirectoryEvent>()
                .eq(SysDirectoryEvent::getProvider, provider)
                .eq(SysDirectoryEvent::getEventId, eventId));
        if (exists != null && exists > 0) {
            return result(eventId, true, true);
        }

        SysDirectoryEvent row = new SysDirectoryEvent();
        row.setProvider(provider);
        row.setEventId(eventId);
        row.setEventType(event.getEventType());
        row.setStatus(SysDirectoryEvent.STATUS_RECEIVED);
        row.setReceivedTime(LocalDateTime.now());
        try {
            row.setPayload(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            row.setPayload(null);
        }
        eventMapper.insert(row);

        try {
            apply(provider, event);
            row.setStatus(SysDirectoryEvent.STATUS_PROCESSED);
            row.setProcessedTime(LocalDateTime.now());
            eventMapper.updateById(row);
            log.info("通讯录推送事件已处理: provider={}, eventId={}, type={}",
                    provider, eventId, event.getEventType());
            return result(eventId, false, true);
        } catch (Exception e) {
            row.setStatus(SysDirectoryEvent.STATUS_FAILED);
            row.setErrorMsg(truncate(e.getMessage(), 1000));
            row.setProcessedTime(LocalDateTime.now());
            eventMapper.updateById(row);
            log.error("通讯录推送事件处理失败: provider={}, eventId={}, type={}",
                    provider, eventId, event.getEventType(), e);
            return result(eventId, false, false);
        }
    }

    private void apply(String provider, DirectoryEvent event) {
        String corpId = syncService.resolveCorpId(provider);
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("外部来源未配置企业ID: " + provider);
        }
        switch (event.getEventType()) {
            case DirectoryEvent.USER_CREATED:
            case DirectoryEvent.USER_UPDATED:
                requireUserId(event);
                if (Boolean.FALSE.equals(event.getActive())) {
                    syncService.deactivateUser(provider, corpId, event.getExternalUserId());
                } else {
                    syncService.upsertMember(provider, corpId, toMember(event));
                }
                break;
            case DirectoryEvent.USER_DELETED:
                requireUserId(event);
                syncService.deactivateUser(provider, corpId, event.getExternalUserId());
                break;
            case DirectoryEvent.DEPT_CREATED:
            case DirectoryEvent.DEPT_UPDATED: {
                requireDeptId(event);
                Long parentLocalId = properties.getRootDeptId();
                if (StringUtils.hasText(event.getParentExternalDeptId())) {
                    Long mapped = syncService.findDeptLocalId(provider, corpId,
                            event.getParentExternalDeptId());
                    if (mapped != null) {
                        parentLocalId = mapped;
                    }
                }
                syncService.upsertDepartment(provider, corpId, toDepartment(event), parentLocalId);
                break;
            }
            case DirectoryEvent.DEPT_DELETED:
                requireDeptId(event);
                syncService.deactivateDepartment(provider, corpId, event.getExternalDeptId());
                break;
            default:
                throw new BusinessException("不支持的事件类型: " + event.getEventType());
        }
    }

    private ExternalMember toMember(DirectoryEvent event) {
        ExternalMember member = new ExternalMember();
        member.setExternalUserId(event.getExternalUserId());
        member.setName(event.getName());
        member.setMobile(event.getMobile());
        member.setEmail(event.getEmail());
        member.setDepartmentIds(event.getDepartmentIds());
        member.setActive(!Boolean.FALSE.equals(event.getActive()));
        return member;
    }

    private ExternalDepartment toDepartment(DirectoryEvent event) {
        return new ExternalDepartment(event.getExternalDeptId(), event.getDeptName(),
                event.getParentExternalDeptId(), event.getSort() == null ? 0 : event.getSort());
    }

    private void requireUserId(DirectoryEvent event) {
        if (!StringUtils.hasText(event.getExternalUserId())) {
            throw new BusinessException("externalUserId 不能为空");
        }
    }

    private void requireDeptId(DirectoryEvent event) {
        if (!StringUtils.hasText(event.getExternalDeptId())) {
            throw new BusinessException("externalDeptId 不能为空");
        }
    }

    private Map<String, Object> result(String eventId, boolean duplicate, boolean processed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("duplicate", duplicate);
        result.put("processed", processed);
        return result;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
