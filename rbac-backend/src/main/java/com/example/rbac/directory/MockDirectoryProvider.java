package com.example.rbac.directory;

import com.example.rbac.config.DirectorySyncProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mock 通讯录（仅测试）：固定 3 个部门、2 个成员
 */
@Component
@RequiredArgsConstructor
public class MockDirectoryProvider implements DirectoryProvider {

    public static final String PROVIDER = "mock";

    private final DirectorySyncProperties properties;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isEnabled() {
        return properties.isMockEnabled() && properties.includes(PROVIDER);
    }

    @Override
    public List<ExternalDepartment> fetchDepartments() {
        List<ExternalDepartment> list = new ArrayList<>();
        list.add(new ExternalDepartment("10", "研发部", null, 1));
        list.add(new ExternalDepartment("11", "平台组", "10", 1));
        list.add(new ExternalDepartment("20", "市场部", null, 2));
        return list;
    }

    @Override
    public List<ExternalMember> fetchMembers() {
        List<ExternalMember> list = new ArrayList<>();
        list.add(member("mock-user-001", "Mock User", "13800000001", "mock1@example.com", "11"));
        list.add(member("mock-user-002", "Mock Two", "13800000002", "mock2@example.com", "20"));
        return list;
    }

    private ExternalMember member(String userId, String name, String mobile, String email, String deptId) {
        ExternalMember member = new ExternalMember();
        member.setExternalUserId(userId);
        member.setName(name);
        member.setMobile(mobile);
        member.setEmail(email);
        member.setDepartmentIds(Arrays.asList(deptId));
        return member;
    }
}
