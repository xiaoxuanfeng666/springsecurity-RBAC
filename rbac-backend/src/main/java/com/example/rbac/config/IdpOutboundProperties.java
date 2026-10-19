package com.example.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * RBAC → 外部通讯录（企业微信/钉钉）出站推送配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "idp.outbound")
public class IdpOutboundProperties {

    private boolean enabled = false;

    /** 目标提供方：wecom / dingtalk / mock，可多个 */
    private List<String> providers = Collections.singletonList("mock");

    /** 最大重试次数 */
    private int maxRetries = 5;

    /** 后台处理间隔（毫秒） */
    private long processIntervalMs = 5000;

    /** 非企业微信/钉钉来源使用的企业ID */
    private String corpId = "mock-corp";

    /** 本地根部门ID（映射到企业微信/钉钉的根部门 1） */
    private Long rootDeptId = 1L;

    private String dingtalkCorpId = "ding-corp";

    private String wecomCorpId;

    private String wecomSecret;

    private String wecomApiBaseUrl = "https://qyapi.weixin.qq.com";

    private String dingtalkAppKey;

    private String dingtalkAppSecret;

    private String dingtalkApiBaseUrl = "https://oapi.dingtalk.com";

    public boolean includes(String provider) {
        return provider != null && providers != null
                && providers.stream().anyMatch(p -> p.equalsIgnoreCase(provider));
    }
}
