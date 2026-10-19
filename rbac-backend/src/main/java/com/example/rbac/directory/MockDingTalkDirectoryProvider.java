package com.example.rbac.directory;

import com.example.rbac.config.DirectorySyncProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mock 钉钉通讯录（仅测试）
 */
@Component
@RequiredArgsConstructor
public class MockDingTalkDirectoryProvider implements DirectoryProvider {

    public static final String PROVIDER = "dingtalk";

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
        list.add(new ExternalDepartment("100", "销售部", null, 1));
        list.add(new ExternalDepartment("101", "华东大区", "100", 1));
        list.add(new ExternalDepartment("200", "财务部", null, 2));
        return list;
    }

    @Override
    public List<ExternalMember> fetchMembers() {
        List<ExternalMember> list = new ArrayList<>();
        list.add(member("ding-user-001", "Ding One", "13900000001", "ding1@example.com", "101"));
        list.add(member("ding-user-002", "Ding Two", "13900000002", "ding2@example.com", "200"));
        return list;
    }

    private ExternalMember member(String userId, String name, String mobile, String email, String deptId) {
        ExternalMember member = new ExternalMember();
        member.setExternalUserId(userId);
        member.setName(name);
        member.setMobile(mobile);
        member.setEmail(email);
        member.setDepartmentIds(Collections.singletonList(deptId));
        return member;
    }
}
