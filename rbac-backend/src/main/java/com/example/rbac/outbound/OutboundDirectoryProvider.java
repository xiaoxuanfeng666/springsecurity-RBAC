package com.example.rbac.outbound;

/**
 * RBAC → 外部通讯录 出站推送 SPI
 */
public interface OutboundDirectoryProvider {

    String provider();

    boolean isEnabled();

    /** @return 外部用户ID（通常是传入的 externalUserId） */
    String createUser(OutboundUser user);

    void updateUser(OutboundUser user);

    void deleteUser(String externalUserId);

    /** @return 外部部门ID */
    String createDepartment(OutboundDepartment dept);

    void updateDepartment(OutboundDepartment dept);

    void deleteDepartment(String externalDeptId);
}
