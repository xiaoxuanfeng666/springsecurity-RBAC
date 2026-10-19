package com.example.rbac.outbound;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 出站提供方（仅测试）：只记录日志，不访问外部系统
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockOutboundProvider implements OutboundDirectoryProvider {

    public static final String PROVIDER = "mock";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String createUser(OutboundUser user) {
        log.info("[OUTBOUND-MOCK] createUser: {}", user);
        return user.getExternalUserId();
    }

    @Override
    public void updateUser(OutboundUser user) {
        log.info("[OUTBOUND-MOCK] updateUser: {}", user);
    }

    @Override
    public void deleteUser(String externalUserId) {
        log.info("[OUTBOUND-MOCK] deleteUser: externalUserId={}", externalUserId);
    }

    @Override
    public String createDepartment(OutboundDepartment dept) {
        String externalId = "MOCK-" + dept.getDeptId();
        log.info("[OUTBOUND-MOCK] createDepartment: {} -> {}", dept, externalId);
        return externalId;
    }

    @Override
    public void updateDepartment(OutboundDepartment dept) {
        log.info("[OUTBOUND-MOCK] updateDepartment: {}", dept);
    }

    @Override
    public void deleteDepartment(String externalDeptId) {
        log.info("[OUTBOUND-MOCK] deleteDepartment: externalDeptId={}", externalDeptId);
    }
}
