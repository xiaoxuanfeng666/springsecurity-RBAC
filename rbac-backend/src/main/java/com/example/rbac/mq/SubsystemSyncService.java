package com.example.rbac.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rbac.entity.SysRole;
import com.example.rbac.entity.SysRoleSubsystemMenu;
import com.example.rbac.entity.SysSubsystem;
import com.example.rbac.entity.SysSubsystemMenu;
import com.example.rbac.entity.SysUser;
import com.example.rbac.entity.SysUserRole;
import com.example.rbac.mapper.SysRoleMapper;
import com.example.rbac.mapper.SysRoleSubsystemMenuMapper;
import com.example.rbac.mapper.SysSubsystemMapper;
import com.example.rbac.mapper.SysSubsystemMenuMapper;
import com.example.rbac.mapper.SysUserMapper;
import com.example.rbac.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 向各子系统下发「用户-角色-权限-菜单」快照。
 * <p>
 * 发送全量快照到 topic {@code RBAC_SUBSYSTEM_SYNC}，tag 为子系统编码，
 * 子系统按 tag 过滤只消费自己那条。消息体是 JSON 字符串（跨服务不共享 DTO 类）。
 * <p>
 * 触发时机：子系统菜单增删改、角色子系统授权变更、用户增删改与角色变更，
 * 以及应用启动完成时全量兜底（保证子系统后启动也能拿到数据）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubsystemSyncService {

    /** 子系统同步 topic */
    public static final String TOPIC = "RBAC_SUBSYSTEM_SYNC";

    /** 状态：1启用 */
    private static final int STATUS_ENABLED = 1;

    private final SysSubsystemMapper subsystemMapper;
    private final SysSubsystemMenuMapper subsystemMenuMapper;
    private final SysRoleSubsystemMenuMapper roleSubsystemMenuMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final ObjectMapper objectMapper;

    /**
     * 用 ObjectProvider 而不是直接注入：未配 rocketmq.name-server 时 starter 不会创建该 Bean，
     * 直接注入会导致整个应用起不来 —— 这个同步能力是附加的，不该拖垮 RBAC 本体。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    @Value("${rbac.subsystem-sync.enabled:true}")
    private boolean enabled;

    @Value("${rocketmq.name-server:}")
    private String nameServer;

    /**
     * 应用启动完成后全量下发一次，兜底补齐（子系统可能先于 RBAC 启动，或消息曾丢失）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncAllOnStartup() {
        if (!enabled) {
            return;
        }
        try {
            syncAll();
        } catch (Exception e) {
            log.error("启动时全量下发子系统数据失败，不影响 RBAC 本体运行", e);
        }
    }

    /**
     * 下发全部启用的子系统
     */
    public void syncAll() {
        if (!enabled) {
            return;
        }
        afterCommit(this::doSyncAll);
    }

    private void doSyncAll() {
        List<SysSubsystem> subsystems = subsystemMapper.selectList(
                new LambdaQueryWrapper<SysSubsystem>().eq(SysSubsystem::getStatus, STATUS_ENABLED));
        for (SysSubsystem subsystem : subsystems) {
            doSync(subsystem);
        }
    }

    /**
     * 下发单个子系统
     */
    public void sync(Long subsystemId) {
        if (!enabled || subsystemId == null) {
            return;
        }
        afterCommit(() -> doSyncById(subsystemId));
    }

    private void doSyncById(Long subsystemId) {
        SysSubsystem subsystem = subsystemMapper.selectById(subsystemId);
        if (subsystem == null || !Integer.valueOf(STATUS_ENABLED).equals(subsystem.getStatus())) {
            // 子系统被停用/删除，不再下发：子系统收不到新消息就不会刷新，保留最后一次的授权
            log.info("子系统不存在或已停用，跳过下发: subsystemId={}", subsystemId);
            return;
        }
        doSync(subsystem);
    }

    private void doSync(SysSubsystem subsystem) {
        SubsystemSyncMessage message;
        try {
            message = build(subsystem);
        } catch (Exception e) {
            log.error("组装子系统同步消息失败: subsystemCode={}", subsystem.getSubsystemCode(), e);
            return;
        }

        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("RocketMQ 未配置（rocketmq.name-server 为空），跳过下发: subsystemCode={}",
                    subsystem.getSubsystemCode());
            return;
        }
        // 按 tag 投递，子系统用 tag 过滤只消费自己那条
        String destination = TOPIC + ":" + subsystem.getSubsystemCode();
        try {
            String payload = objectMapper.writeValueAsString(message);
            template.syncSend(destination, payload);
            log.info("已下发子系统数据: destination={}, menus={}, roleMenus={}, userRoles={}, users={}",
                    destination, message.getMenus().size(), message.getRoleMenus().size(),
                    message.getUserRoles().size(), message.getUsers().size());
        } catch (Exception e) {
            // 发消息失败不能影响 RBAC 本体的写操作：子系统暂时数据陈旧，下次变更或重启会补上
            log.error("下发子系统数据失败: destination={}", destination, e);
        }
    }

    private SubsystemSyncMessage build(SysSubsystem subsystem) {
        SubsystemSyncMessage message = new SubsystemSyncMessage();
        message.setSubsystemId(subsystem.getId());
        message.setSubsystemCode(subsystem.getSubsystemCode());
        message.setSubsystemName(subsystem.getSubsystemName());

        // 该子系统的全部菜单：启用的和停用的都带上，由子系统按 status 自行过滤
        List<SysSubsystemMenu> menus = subsystemMenuMapper.selectList(
                new LambdaQueryWrapper<SysSubsystemMenu>()
                        .eq(SysSubsystemMenu::getSubsystemId, subsystem.getId())
                        .orderByAsc(SysSubsystemMenu::getParentId, SysSubsystemMenu::getSort));
        List<Long> menuIds = menus.stream().map(SysSubsystemMenu::getId).collect(Collectors.toList());
        message.setMenus(menus.stream().map(this::toMenu).collect(Collectors.toList()));

        // 角色标识与可用角色：只认启用且未删除的角色（已删角色的授权行不会被清理，必须在这里滤掉）
        Map<Long, String> roleKeysById = roleMapper.selectList(
                        new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, STATUS_ENABLED))
                .stream().collect(Collectors.toMap(SysRole::getId, SysRole::getRoleKey, (a, b) -> a));

        if (!menuIds.isEmpty()) {
            message.setRoleMenus(roleSubsystemMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleSubsystemMenu>()
                                    .in(SysRoleSubsystemMenu::getSubsystemMenuId, menuIds))
                    .stream()
                    .filter(row -> roleKeysById.containsKey(row.getRoleId()))
                    .map(row -> {
                        SubsystemSyncMessage.RoleMenu item = new SubsystemSyncMessage.RoleMenu();
                        item.setRoleId(row.getRoleId());
                        item.setMenuId(row.getSubsystemMenuId());
                        return item;
                    }).collect(Collectors.toList()));
        }

        message.setUsers(buildUsers(roleKeysById));
        message.setUserRoles(buildUserRoles(roleKeysById.keySet()));
        return message;
    }

    private SubsystemSyncMessage.Menu toMenu(SysSubsystemMenu menu) {
        SubsystemSyncMessage.Menu item = new SubsystemSyncMessage.Menu();
        item.setId(menu.getId());
        item.setParentId(menu.getParentId());
        item.setMenuName(menu.getMenuName());
        item.setType(menu.getType());
        item.setPath(menu.getPath());
        item.setComponent(menu.getComponent());
        item.setIcon(menu.getIcon());
        item.setSort(menu.getSort());
        item.setVisible(menu.getVisible());
        item.setStatus(menu.getStatus());
        item.setPerms(menu.getPerms());
        return item;
    }

    /**
     * 用户及其角色标识。只下发在职用户 —— 停用/离职用户不该能在子系统登录。
     * <p>
     * roleKeys 必须带上：超级管理员的授权不落库，子系统靠它识别超管身份。
     */
    private List<SubsystemSyncMessage.User> buildUsers(Map<Long, String> roleKeysById) {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, STATUS_ENABLED));
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, List<String>> roleKeysByUser = new HashMap<>();
        for (SysUserRole userRole : userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds))) {
            String roleKey = roleKeysById.get(userRole.getRoleId());
            if (roleKey != null) {
                roleKeysByUser.computeIfAbsent(userRole.getUserId(), k -> new ArrayList<>()).add(roleKey);
            }
        }

        List<SubsystemSyncMessage.User> result = new ArrayList<>(users.size());
        for (SysUser user : users) {
            SubsystemSyncMessage.User item = new SubsystemSyncMessage.User();
            item.setId(user.getId());
            item.setUsername(user.getUsername());
            item.setNickname(user.getNickname());
            item.setStatus(user.getStatus());
            item.setRoleKeys(roleKeysByUser.getOrDefault(user.getId(), new ArrayList<>()));
            result.add(item);
        }
        return result;
    }

    private List<SubsystemSyncMessage.UserRole> buildUserRoles(Set<Long> enabledRoleIds) {
        return userRoleMapper.selectList(null).stream()
                .filter(row -> enabledRoleIds.contains(row.getRoleId()))
                .map(row -> {
                    SubsystemSyncMessage.UserRole item = new SubsystemSyncMessage.UserRole();
                    item.setUserId(row.getUserId());
                    item.setRoleId(row.getRoleId());
                    return item;
                }).collect(Collectors.toList());
    }

    /**
     * 事务提交后再发送
     * <p>
     * 同步消息必须在数据落库之后发：事务里发的话，一旦回滚，子系统就拿到了并不存在的数据。
     */
    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
