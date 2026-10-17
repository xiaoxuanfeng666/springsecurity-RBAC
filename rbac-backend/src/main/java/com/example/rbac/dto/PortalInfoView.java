package com.example.rbac.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 门户首页的「当前登录状态」
 * <p>
 * {@code clients} 直接来自 SSO 会话（{@code sso:session:{username}}.clients），是<b>服务端记录的事实</b>
 * —— 哪些子系统确实已经给这个人签发过令牌。门户的一键登录正是用它来判断进度：
 * 某个子系统的 clientId 出现在这里，就说明那个子系统的令牌已经到手，可以进入下一个了。
 * 这比在前端「猜」iframe 加载完没有要可靠得多。
 */
@Data
public class PortalInfoView implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    private String nickname;

    /** 本次 SSO 会话的登录时间 */
    private String loginTime;

    /** 已登录过的客户端 id 列表，如 [portal, order-center] */
    private List<String> clients;
}
