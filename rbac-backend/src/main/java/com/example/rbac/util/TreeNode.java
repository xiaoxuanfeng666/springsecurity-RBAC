package com.example.rbac.util;

import java.util.List;

/**
 * 可构建为树的节点（菜单、部门等）
 */
public interface TreeNode<T> {

    Long getId();

    Long getParentId();

    Integer getSort();

    List<T> getChildren();
}
