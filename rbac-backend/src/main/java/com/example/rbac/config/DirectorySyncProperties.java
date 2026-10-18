package com.example.rbac.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部通讯录 / 部门同步配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "idp.directory-sync")
public class DirectorySyncProperties {

    /** 是否启用定时同步 */
    private boolean enabled = false;

    /** 同步提供方：mock / wecom */
    private String provider = "mock";

    /** 是否允许 mock 提供方（仅测试） */
    private boolean mockEnabled = false;

    /** 定时表达式 */
    private String cron = "0 0 3 * * ?";

    /** 外部部门挂载到的本地根节点 */
    private Long rootDeptId = 1L;

    /** 外部成员在本地不存在时是否自动建号 */
    private boolean createUsers = true;

    /** 自动建号的默认角色标识，逗号分隔 */
    private String defaultRoleKeys = "common";

    /** 自动建号的用户名前缀 */
    private String usernamePrefix = "idp_";

    /** mock 等非企业微信提供方使用的企业ID */
    private String corpId = "mock-corp";

    /** 企业微信通讯录同步配置 */
    private String wecomCorpId;

    private String wecomSecret;

    private String wecomApiBaseUrl = "https://qyapi.weixin.qq.com";
}
