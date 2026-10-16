package com.example.order.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 将扁平列表组装为树，并按 sort 升序递归排序。
     * 父节点不在列表中的节点会被提升为顶级，避免权限范围不全时节点丢失。
     */
    public static <T extends TreeNode<T>> List<T> buildTree(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, T> idMap = nodes.stream()
                .collect(Collectors.toMap(TreeNode::getId, Function.identity(), (a, b) -> a));

        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            Long parentId = node.getParentId();
            T parent = parentId == null ? null : idMap.get(parentId);
            if (parent == null || parent.getId().equals(node.getId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        sort(roots);
        return roots;
    }

    private static <T extends TreeNode<T>> void sort(List<T> nodes) {
        nodes.sort(Comparator.comparing(n -> n.getSort() == null ? 0 : n.getSort()));
        for (T node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sort(node.getChildren());
            }
        }
    }
}
