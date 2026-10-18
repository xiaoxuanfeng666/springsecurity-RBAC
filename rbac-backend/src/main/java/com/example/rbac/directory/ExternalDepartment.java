package com.example.rbac.directory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 外部部门
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalDepartment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String externalId;

    private String name;

    private String parentExternalId;

    private int sort;
}
