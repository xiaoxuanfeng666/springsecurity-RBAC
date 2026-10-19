package com.example.rbac.outbound;

import lombok.Data;

import java.io.Serializable;

/**
 * 出站推送的部门
 */
@Data
public class OutboundDepartment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long deptId;

    /** 外部部门ID；创建时为空，创建成功后由外部返回并写入映射 */
    private String externalDeptId;

    private String name;

    private String parentExternalDeptId;

    /** 本地父部门ID；出站处理时用于解析父部门的外部ID */
    private Long parentDeptId;

    private int sort;
}
