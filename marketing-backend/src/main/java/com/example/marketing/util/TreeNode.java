package com.example.marketing.util;

import java.util.List;

/**
 * 可构建为树的节点
 */
public interface TreeNode<T> {

    Long getId();

    Long getParentId();

    Integer getSort();

    List<T> getChildren();
}
