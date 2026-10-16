package com.example.order.service;

import com.example.order.entity.OrderMenu;
import com.example.order.entity.OrderRoleMenu;
import com.example.order.entity.OrderUser;
import com.example.order.entity.OrderUserRole;
import com.example.order.mapper.OrderMenuMapper;
import com.example.order.mapper.OrderRoleMenuMapper;
import com.example.order.mapper.OrderUserMapper;
import com.example.order.mapper.OrderUserRoleMapper;
import com.example.order.mq.SubsystemSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 把 RBAC 下发的快照落到本地的同步表
 * <p>
 * 收到消息即**清空重建**这四张表：消息本身是全量快照，重建比逐条 diff 简单可靠得多，
 * 也不会因为漏处理某条删除而留下过期授权。
 * <p>
 * 整个过程在一个事务里：清空到一半失败的话，回滚保住旧数据，
 * 宁可继续用上一次的快照，也不要留下半套权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

    /** 本系统在 RBAC 里登记的子系统编码（消息的 tag 也是它） */
    public static final String SUBSYSTEM_CODE = "order-center";

    private final OrderUserMapper userMapper;
    private final OrderMenuMapper menuMapper;
    private final OrderRoleMenuMapper roleMenuMapper;
    private final OrderUserRoleMapper userRoleMapper;

    @Transactional(rollbackFor = Exception.class)
    public void rebuild(SubsystemSyncMessage message) {
        // 用户为空只可能是异常情况（RBAC 侧总是下发全部在职用户）：
        // 直接按这份快照重建会让所有人都登不进来，宁可保留上一次的数据
        if (message.getUsers() == null || message.getUsers().isEmpty()) {
            log.warn("收到的同步消息里没有任何用户，跳过重建以免清空现有授权: subsystemCode={}",
                    message.getSubsystemCode());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        userRoleMapper.delete(null);
        roleMenuMapper.delete(null);
        menuMapper.delete(null);
        userMapper.delete(null);

        for (SubsystemSyncMessage.Menu menu : message.getMenus()) {
            OrderMenu row = new OrderMenu();
            row.setId(menu.getId());
            row.setParentId(menu.getParentId() == null ? 0L : menu.getParentId());
            row.setMenuName(menu.getMenuName());
            row.setType(menu.getType());
            row.setPerms(menu.getPerms());
            row.setPath(menu.getPath());
            row.setComponent(menu.getComponent());
            row.setIcon(menu.getIcon());
            row.setSort(menu.getSort() == null ? 0 : menu.getSort());
            row.setVisible(menu.getVisible() == null ? 1 : menu.getVisible());
            row.setStatus(menu.getStatus() == null ? 1 : menu.getStatus());
            row.setSyncTime(now);
            menuMapper.insert(row);
        }

        for (SubsystemSyncMessage.RoleMenu roleMenu : message.getRoleMenus()) {
            OrderRoleMenu row = new OrderRoleMenu();
            row.setRoleId(roleMenu.getRoleId());
            row.setMenuId(roleMenu.getMenuId());
            row.setSyncTime(now);
            roleMenuMapper.insert(row);
        }

        for (SubsystemSyncMessage.User user : message.getUsers()) {
            OrderUser row = new OrderUser();
            row.setId(user.getId());
            row.setUsername(user.getUsername());
            row.setNickname(user.getNickname());
            row.setStatus(user.getStatus() == null ? 1 : user.getStatus());
            row.setRoleKeys(joinRoleKeys(user.getRoleKeys()));
            row.setSyncTime(now);
            userMapper.insert(row);
        }

        for (SubsystemSyncMessage.UserRole userRole : message.getUserRoles()) {
            OrderUserRole row = new OrderUserRole();
            row.setUserId(userRole.getUserId());
            row.setRoleId(userRole.getRoleId());
            row.setSyncTime(now);
            userRoleMapper.insert(row);
        }

        log.info("已重建子系统同步数据: subsystemCode={}, menus={}, roleMenus={}, users={}, userRoles={}",
                message.getSubsystemCode(), message.getMenus().size(), message.getRoleMenus().size(),
                message.getUsers().size(), message.getUserRoles().size());
    }

    /**
     * 角色标识存成逗号分隔的一列
     * <p>
     * 角色在这里只用来判断「是不是超级管理员」，没有按角色查询的需求，
     * 不值得为它单独建表
     */
    private String joinRoleKeys(List<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) {
            return null;
        }
        return roleKeys.stream().distinct().collect(Collectors.joining(","));
    }
}
