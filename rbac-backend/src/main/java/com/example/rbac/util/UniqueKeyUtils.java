package com.example.rbac.util;

/**
 * 逻辑删除时腾出业务唯一键
 * <p>
 * 删除走的是逻辑删除（del_flag=1），行仍物理留在表里，唯一键也就仍被占用。
 * 若不在删除时改名，被删掉的用户名 / 角色标识 / 岗位编码将**永远无法再创建** ——
 * 应用层的重名校验排除了已删行所以会放行，随后 INSERT 撞上数据库唯一键，用户只看到「系统异常」。
 * <p>
 * 改名规则 {@code 原名_deleted_{id}}：id 是主键、全局唯一，因此改出来的值不可能与任何现存值冲突。
 */
public final class UniqueKeyUtils {

    private static final String MARK = "_deleted_";

    private UniqueKeyUtils() {
    }

    /**
     * @param value     原值
     * @param id        行主键，用于保证唯一
     * @param maxLength 目标列长度（VARCHAR 按字符计），超长时截断前面的原名部分
     */
    public static String release(String value, Long id, int maxLength) {
        if (value == null) {
            return null;
        }
        String suffix = MARK + id;
        int keep = maxLength - suffix.length();
        if (keep <= 0) {
            return suffix;
        }
        String head = value.length() > keep ? value.substring(0, keep) : value;
        return head + suffix;
    }
}
