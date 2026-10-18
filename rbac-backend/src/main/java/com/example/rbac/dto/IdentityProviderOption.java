package com.example.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 外部身份提供方选项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityProviderOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;

    private String label;
}
