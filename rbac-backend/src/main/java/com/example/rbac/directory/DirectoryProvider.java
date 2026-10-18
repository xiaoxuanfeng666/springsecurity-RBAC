package com.example.rbac.directory;

import java.util.List;

/**
 * 通讯录提供方 SPI（wecom / mock ...）
 */
public interface DirectoryProvider {

    String provider();

    boolean isEnabled();

    List<ExternalDepartment> fetchDepartments();

    List<ExternalMember> fetchMembers();
}
