package com.example.oauth.identity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部身份提供方返回的用户身份
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String provider;

    private String corpId;

    private String externalUserId;

    private String unionId;

    private String displayName;

    private String mobile;

    private String email;

    private List<String> departmentIds = new ArrayList<>();
}
