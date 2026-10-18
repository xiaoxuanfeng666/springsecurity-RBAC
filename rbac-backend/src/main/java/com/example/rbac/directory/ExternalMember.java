package com.example.rbac.directory;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部成员
 */
@Data
public class ExternalMember implements Serializable {

    private static final long serialVersionUID = 1L;

    private String externalUserId;

    private String name;

    private String mobile;

    private String email;

    private List<String> departmentIds = new ArrayList<>();

    private boolean active = true;
}
