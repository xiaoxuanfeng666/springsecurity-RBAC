package com.example.oauth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 多身份提供方（IdP）与账号策略
 */
@Data
@Component
@ConfigurationProperties(prefix = "idp")
public class IdpProperties {

    /** 未绑定时是否允许用户输入本地账号密码自助绑定 */
    private boolean selfBindEnabled = true;

    /** 自助绑定页地址（rbac-oauth-web） */
    private String bindPageUrl = "http://localhost:5174/bind";

    /** 是否启用 mock 身份提供方（仅开发/测试） */
    private boolean mockEnabled = false;

    private Mock mock = new Mock();

    private Jit jit = new Jit();

    @Data
    public static class Mock {
        private String provider = "mock";
        private String corpId = "mock-corp";
        private String externalUserId = "mock-user-001";
        private String displayName = "Mock User";
        private String mobile = "13800000000";
        private String email = "mock@example.com";
        private List<String> departmentIds = java.util.Collections.singletonList("10");
    }

    @Data
    public static class Jit {
        /** 未绑定时是否自动创建本地用户 */
        private boolean enabled = false;

        /** 自动建号归属的本地组织节点 */
        private Long defaultDeptId = 1L;

        /** 自动建号默认角色标识，逗号分隔 */
        private String defaultRoleKeys = "common";

        /** 用户名前缀，最终用户名 = 前缀 + 外部 userid（非法字符替换为下划线） */
        private String usernamePrefix = "idp_";
    }
}
