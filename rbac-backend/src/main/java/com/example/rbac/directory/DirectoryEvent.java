package com.example.rbac.directory;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部系统推送的用户/部门变更事件（统一格式）
 * <p>
 * 真实的企业微信/钉钉回调由各自的适配层解密、转换成这个结构后再交给同步服务。
 */
@Data
public class DirectoryEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String DEPT_CREATED = "DEPT_CREATED";
    public static final String DEPT_UPDATED = "DEPT_UPDATED";
    public static final String DEPT_DELETED = "DEPT_DELETED";

    /** 外部事件唯一ID，用于幂等去重；缺省时由服务端生成 */
    private String eventId;

    /** USER_* / DEPT_* */
    private String eventType;

    // ---------- 用户事件 ----------
    private String externalUserId;
    private String name;
    private String mobile;
    private String email;
    private List<String> departmentIds = new ArrayList<>();
    private Boolean active = true;

    // ---------- 部门事件 ----------
    private String externalDeptId;
    private String deptName;
    private String parentExternalDeptId;
    private Integer sort;
}
