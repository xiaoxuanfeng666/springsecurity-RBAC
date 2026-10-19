package com.example.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserDept;
import com.example.rbac.mapper.SysUserDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户与部门关联（多部门）
 */
@Service
@RequiredArgsConstructor
public class SysUserDeptService {

    private final SysUserDeptMapper userDeptMapper;

    /**
     * 用表单值整体替换用户的部门关联；主部门排第一。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(Long userId, Long mainDeptId, Collection<Long> deptIds,
                        Boolean leaderInDept, Integer orderInDept) {
        userDeptMapper.delete(new LambdaQueryWrapper<SysUserDept>()
                .eq(SysUserDept::getUserId, userId));
        LinkedHashSet<Long> all = new LinkedHashSet<>();
        if (mainDeptId != null) {
            all.add(mainDeptId);
        }
        if (deptIds != null) {
            deptIds.stream().filter(Objects::nonNull).forEach(all::add);
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long deptId : all) {
            boolean main = deptId.equals(mainDeptId);
            SysUserDept row = new SysUserDept();
            row.setUserId(userId);
            row.setDeptId(deptId);
            row.setIsMain(main ? 1 : 0);
            row.setIsLeader(main && Boolean.TRUE.equals(leaderInDept) ? 1 : 0);
            row.setOrderInDept(main && orderInDept != null ? orderInDept : 0);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userDeptMapper.insert(row);
        }
    }

    public List<Long> deptIds(Long userId) {
        return userDeptMapper.selectList(new LambdaQueryWrapper<SysUserDept>()
                        .eq(SysUserDept::getUserId, userId)
                        .orderByDesc(SysUserDept::getIsMain)
                        .orderByAsc(SysUserDept::getId))
                .stream().map(SysUserDept::getDeptId).collect(Collectors.toList());
    }

    public void deleteByUser(Long userId) {
        userDeptMapper.delete(new LambdaQueryWrapper<SysUserDept>()
                .eq(SysUserDept::getUserId, userId));
    }

    public void fill(SysUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        fill(Collections.singletonList(user));
    }

    public void fill(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(SysUser::getId)
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, List<SysUserDept>> byUser = userDeptMapper.selectList(
                        new LambdaQueryWrapper<SysUserDept>()
                                .in(SysUserDept::getUserId, userIds)
                                .orderByDesc(SysUserDept::getIsMain)
                                .orderByAsc(SysUserDept::getId))
                .stream().collect(Collectors.groupingBy(SysUserDept::getUserId));

        for (SysUser user : users) {
            List<SysUserDept> rows = byUser.getOrDefault(user.getId(), new ArrayList<>());
            if (rows.isEmpty()) {
                user.setDeptIds(user.getDeptId() == null
                        ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(user.getDeptId())));
                user.setLeaderInDept(false);
                user.setOrderInDept(0);
                continue;
            }
            user.setDeptIds(rows.stream().map(SysUserDept::getDeptId).collect(Collectors.toList()));
            SysUserDept main = rows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getIsMain()))
                    .findFirst().orElse(rows.get(0));
            user.setLeaderInDept(Integer.valueOf(1).equals(main.getIsLeader()));
            user.setOrderInDept(main.getOrderInDept() == null ? 0 : main.getOrderInDept());
        }
    }
}
