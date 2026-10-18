package com.example.oauth.identity;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 自助绑定的待处理凭据（5 分钟）
 */
@Data
public class BindTicket implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String corpId;

    private String externalUserId;

    private String unionId;

    private String displayName;

    private String mobile;

    private String email;

    private List<String> departmentIds = new ArrayList<>();

    private String returnTo;

    private int attempts;

    public static BindTicket from(ExternalIdentity identity, String returnTo) {
        BindTicket ticket = new BindTicket();
        ticket.setProvider(identity.getProvider());
        ticket.setCorpId(identity.getCorpId());
        ticket.setExternalUserId(identity.getExternalUserId());
        ticket.setUnionId(identity.getUnionId());
        ticket.setDisplayName(identity.getDisplayName());
        ticket.setMobile(identity.getMobile());
        ticket.setEmail(identity.getEmail());
        ticket.setDepartmentIds(identity.getDepartmentIds());
        ticket.setReturnTo(returnTo);
        return ticket;
    }
}
