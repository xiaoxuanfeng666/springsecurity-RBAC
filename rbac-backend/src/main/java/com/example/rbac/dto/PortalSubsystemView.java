package com.example.rbac.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一门户首页上的一张子系统卡片
 * <p>
 * <b>刻意不含授权地址</b>：那个地址上带 client_secret（本项目对授权端点的定制要求），
 * 只能由后端持有并 302 出去（见 {@code GET /portal/sso-login/{code}}）。
 * 把带密钥的地址下发给前端 JS，等于让密钥出现在网络面板、前端日志与浏览器历史里。
 */
@Data
public class PortalSubsystemView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 子系统编码，如 order-center */
    private String code;

    /** 子系统名称，如 订单中心 */
    private String name;

    /** 子系统对应的 OAuth2 客户端 id（约定等于子系统编码） */
    private String clientId;

    /** 子系统前端的入口地址，如 http://localhost:5176 */
    private String entryUrl;

    /** 是否可用：该子系统在 clientdetails 里有对应且配置齐全的客户端 */
    private boolean ready;

    /** 不可用的原因，供页面提示用；可用时为 null */
    private String unavailableReason;
}
